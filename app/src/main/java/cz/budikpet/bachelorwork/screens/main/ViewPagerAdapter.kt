package cz.budikpet.bachelorwork.screens.main

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import org.joda.time.DateTime

class ViewPagerAdapter(
    fm: FragmentManager,
    private val daysPerFragment: Int,
    private val numOfFragments: Int,
    private val firstDate: DateTime
) :
    FragmentStatePagerAdapter(fm) {

    override fun getCount() = numOfFragments

    override fun getItem(position: Int): Fragment {
        val todayPosition = numOfFragments / 2
        val currDate = when {
            position < todayPosition -> firstDate.minusDays(daysPerFragment * (todayPosition - position))
            position == todayPosition -> firstDate
            else -> firstDate.plusDays(daysPerFragment * (position - todayPosition))
        }

        return MultidayViewFragment.newInstance(daysPerFragment, currDate)
    }


}
