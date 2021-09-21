package i.task.modules.fifo

import i.task.ITaskStatus
import i.task.TaskRollbackInfo
import i.task.TaskStatus
import java.util.Optional

class FIFOTaskStatus<RES : Any>(private val fifoTaskGroup: FIFOTaskGroup<Any, RES>) : ITaskStatus<RES> {
    override var status: TaskStatus = TaskStatus.READY
    override val value: Optional<RES>
        get() = fifoTaskGroup.lastTaskResult()
    override val process: Float
        get() = fifoTaskGroup.process

    override fun cancel() {
        fifoTaskGroup.cancel(TaskRollbackInfo(TaskRollbackInfo.RollbackType.USER_CANCEL))
    }
}