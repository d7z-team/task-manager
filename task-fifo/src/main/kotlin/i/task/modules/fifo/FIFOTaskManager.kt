package i.task.modules.fifo

import i.task.ITask
import i.task.ITaskInfo
import i.task.ITaskManager
import i.task.ITaskStatus
import i.task.TaskCallBack
import i.task.TaskException
import i.task.TaskRollbackInfo
import i.task.TaskRollbackInfo.RollbackType
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.Collections
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * 先进先出单线程串行任务调度算法
 *
 *
 */
class FIFOTaskManager(
    override val name: String = "FIFO",
    threadFactory: ThreadFactory = Executors.defaultThreadFactory(),
    threadPool: ExecutorService = Executors.newCachedThreadPool()
) : ITaskManager, ITaskInfo {
    private val marker = MarkerFactory.getMarker("任务管理器：\"$name\"")

    private val shutdown = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val condition = lock.newCondition() // 锁
    private val taskGroups = LinkedBlockingDeque<IFIFOTaskGroup<Any>>() // 任务组
    private val callBackThreadPool: ExecutorService = threadPool // 任务回调执行线程
    private val tasks = Collections.synchronizedSet(HashSet<ITask<*>>())

    init {
        val thread = threadFactory.newThread {
            while (taskGroups.isNotEmpty() || shutdown.get().not()) {
                if (taskGroups.isEmpty()) {
                    lock.lock()
                    condition.await()
                    lock.unlock()
                } else {
                    runTaskGroup()
                }
            }
            clear()
        }
        thread.name = name
        thread.start()
        logger.debug(marker, "工作线程已启动.")
    }

    /**
     * 生命周期结束，清除
     */
    private fun clear() {
        logger.debug(marker, "任务管理器实例已退出.")
        callBackThreadPool.shutdown()
    }

    private fun runTaskGroup() { // 任务队列
        var error = Optional.empty<Throwable>()
        val taskGroup: IFIFOTaskGroup<Any> = taskGroups.pollFirst() ?: return
        try {
            taskGroup.run() // 执行任务
        } catch (e: Throwable) {
            try { // 发生错误，触发任务回滚
                logger.debug(marker, "任务组 \"{}\" 发生故障，开始回滚.", taskGroup.name)
                error = Optional.of(e)
                val type = if (e is TaskException.CheckFailException) {
                    RollbackType.CURRENT_CHECK_ERROR
                } else {
                    RollbackType.CURRENT_RUN_ERROR
                }
                taskGroup.cancel(TaskRollbackInfo(type, error))
            } catch (_: Throwable) {
            }
        } finally {
            try {
                taskGroup.close()
            } catch (_: Throwable) {
            }
        }
        taskGroup.callBack(
            callBackThreadPool, error
        )
        condition.tryLock {
            tasks.removeAll(taskGroup.tasks)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <RES : Any> submit(
        name: String,
        task: List<ITask<*>>,
        call: TaskCallBack<RES>
    ): ITaskStatus<RES> {
        val result = condition.tryLock {
            if (task.isEmpty()) {
                throw NullPointerException("没有任务！")
            }
            if (shutdown.get()) {
                throw RuntimeException("此任务管理器已销毁,无法添加任务!")
            }
            if (Collections.disjoint(this.tasks, task)) { // 排除重复任务
                this.tasks.addAll(task)
                val fifoTaskGroup = FIFOTaskGroup(name, task, call)
                taskGroups.addLast(fifoTaskGroup as IFIFOTaskGroup<Any>)
                lock.lock()
                condition.signalAll()
                lock.unlock()
                fifoTaskGroup.taskStatusCall
            } else {
                throw RuntimeException("某些任务已存在于队列中，在此任务停止之前不允许添加相同任务！")
            }
        }
        call.putHook(result)
        return result
    }

    override fun shutdown() = condition.tryLock {
        logger.debug(marker, "收到销毁信号，从此刻开始不再接收新任务.")
        shutdown.set(true)
        lock.lock()
        condition.signalAll()
        lock.unlock()
    }

    override val process: Float
        get() {
            var process = 0f
            taskGroups.forEach { process += it.process }
            return process / taskGroups.size
        }

    override val size: Int
        get() {
            var size = 0
            taskGroups.forEach { size += it.size }
            return size
        }
    override val taskInfo: ITaskInfo = this
    private fun <T : Any?> Any.tryLock(function: () -> T): T {
        return synchronized(this) {
            function()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FIFOTaskManager::class.java)
    }
}
