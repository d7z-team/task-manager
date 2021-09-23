package samples

import i.task.ITaskContext
import i.task.TaskRollbackInfo
import i.task.extra.task.SimpleTask
import i.task.extra.task.SimpleUnitTask
import i.task.extra.taskManager
import i.task.modules.fifo.FIFOTaskManager

fun main() {
    val taskManager = taskManager(FIFOTaskManager) {
        name = "hello-task-manager"
    } // 创建任务
    taskManager.submit<Int>("hello-tasks")
        .append(PutUserTask("dragon")) // 提交任务
        .append(CheckUserTask())
        .append(CheckAgeTask())
        .success {
            println("任务成功!返回：$it.")
        }.fail {
            println("任务失败")
        }
        .submit()
    taskManager.shutdown()
}

class PutUserTask(private val name: String) : SimpleTask<String>("user.task") {
    override fun run(context: ITaskContext): String {
        logger.info(marker, "姓名：{}.", name)
        context.currentGroup.properties["user.name"] = name
        return name
    }
}

class CheckUserTask : SimpleUnitTask("user.task.check") {
    override fun check(context: ITaskContext): Boolean {
        val lastTaskResult = context.currentGroup.lastTaskResult<String>()
        logger.info(marker, "上个任务返回的结果是 :{}", lastTaskResult.get())
        val name: String = context.currentGroup.getProperty("user.name")
        return name.length > 3
    }

    override fun run(context: ITaskContext) {
        logger.info(marker, "名称合法！")
        context.currentGroup.properties["user.age"] = 16
    }

    override fun rollback(info: TaskRollbackInfo) {
        if (info.isCurrentError) {
            logger.error(marker, "发生错误，名称不合法！")
        } else {
            logger.info(marker, "其他原因导致回滚.")
        }
    }
}

class CheckAgeTask : SimpleTask<Int>("user.task.check.age") {
    override fun check(context: ITaskContext): Boolean {
        return context.currentGroup.getProperty<Int>("user.age") >= 18
    }

    override fun run(context: ITaskContext): Int {
        logger.info(marker, "年龄符合要求！")
        return context.currentGroup.getProperty("user.age")
    }

    override fun rollback(info: TaskRollbackInfo) {
        if (info.isCurrentError) {
            logger.error(marker, "发生错误 ,年龄不符合要求！")
        } else {
            logger.info(marker, "其他原因导致回滚.")
        }
    }
}
