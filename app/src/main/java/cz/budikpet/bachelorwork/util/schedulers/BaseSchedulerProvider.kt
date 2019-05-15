package cz.budikpet.bachelorwork.util.schedulers

import io.reactivex.Scheduler

/**
 * This interface is used for getting the needed RxJava schedulers.
 *
 * We can use DI to pass this interface if we need to change the schedulers.
 */
interface BaseSchedulerProvider {
    fun io(): Scheduler
    fun computation(): Scheduler
    fun ui(): Scheduler
}
