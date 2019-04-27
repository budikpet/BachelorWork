package cz.budikpet.bachelorwork.screens.main

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import org.joda.time.DateTime

class ViewPagerAdapter(fm: FragmentManager, private val daysPerFragment: Int, private val numOfFragments: Int) :
    FragmentStatePagerAdapter(fm) {

//    private val mFragments = SparseArray<MultidayViewFragment?>()

    override fun getCount() = numOfFragments

    override fun getItem(position: Int): Fragment {
        val todayPosition = numOfFragments / 2
        val currDate = when {
            position < todayPosition -> DateTime().minusDays(daysPerFragment * (todayPosition - position))
            else -> DateTime().plusDays(daysPerFragment * (position - todayPosition))
        }

        val fragment = MultidayViewFragment.newInstance(daysPerFragment, currDate)
//        mFragments.put(position, fragment)

        return fragment
    }

//    fun updateScrollY(pos: Int, y: Int) {
//        mFragments[pos - 1]?.updateScrollY(y)
//        mFragments[pos + 1]?.updateScrollY(y)
//    }
//
//    fun updateCalendars(pos: Int) {
//        for (i in -1..1) {
//            mFragments[pos + i]?.updateCalendar()
//        }
//    }
}
