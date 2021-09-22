package i.task

open class TaskException(override val message: String) : RuntimeException(message) {
    class CheckFailException(taskName: String) : TaskException("任务 $taskName 自检未通过")
    class UserExitException(taskName: String) : TaskException("任务组 $taskName 被手动退出")
}
