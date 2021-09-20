package i.task

/**
 * 任务回调函数
 * @property success Function1<T, Unit> 成功回调
 * @property fail Function1<Throwable, Unit> 失败回调
 */
class TaskCallBack<T : Any, RES : Any?> private constructor(
    val success: (T) -> RES,
    val fail: (Throwable) -> RES
) {
    companion object {
        /**
         * 只处理成功回调
         */
        fun <T : Any> success(call: (T) -> Unit) = TaskCallBack(success = call, fail = {})

        /**
         * 只处理失败回调
         */
        fun fail(fail: (Throwable) -> Unit) = TaskCallBack<Any, Unit>(success = {}, fail = fail)

        /**
         * 自定义任务处理建造者
         */
        fun <T : Any, RES : Any?> builder() = TaskCallBackBuilder<T, RES>()

        /**
         *  等待任务结束返回
         */
        fun <T : Any> join(defaultValue: T? = null): TaskCallBack<T, T?> {
            val call: () -> Unit = {
            }
            return builder<T, T?>()
                .success {
                    call()
                    it
                }.fail {
                    defaultValue
                }.build()
        }

        class TaskCallBackBuilder<T : Any, RES : Any?> {

            private lateinit var success: (T) -> RES
            private lateinit var fail: (Throwable) -> RES

            fun success(success: (T) -> RES) = kotlin.run {
                this.success = success
                this
            }

            fun fail(fail: (Throwable) -> RES) = kotlin.run {
                this.fail = fail
                this
            }

            fun build() = TaskCallBack(success, fail)
        }
    }
}
