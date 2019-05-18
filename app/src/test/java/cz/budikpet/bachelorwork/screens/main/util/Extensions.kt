package cz.budikpet.bachelorwork.screens.main.util

import cz.budikpet.bachelorwork.data.models.TimetableEvent
import org.joda.time.DateTime
import org.junit.Test
import org.mockito.Mockito

inline fun <reified T> mock() = Mockito.mock(T::class.java)

fun listsEqual(events1: List<TimetableEvent>, events2: ArrayList<TimetableEvent>): Boolean {
    if (events1.count() == events2.count()) {
        for (event in events1) {
            if (!events2.any { it.fullEqual(event) }) {
                return false
            }
        }

        return true
    }

    return false
}

class ExtensionsTest {
    @Test
    fun testListsEqual() {
        // Data
        val start = DateTime()

        val events1 = arrayListOf(
            TimetableEvent(siriusId = 5, fullName = "E1", starts_at = start.plusHours(3), ends_at = start.plusHours(8)),
            TimetableEvent(siriusId = 6, fullName = "E2", starts_at = start.plusHours(10), ends_at = start.plusHours(11)),
            TimetableEvent(fullName = "E3", starts_at = start.plusHours(21), ends_at = start.plusHours(22)),
            TimetableEvent(fullName = "E4", starts_at = start.plusHours(13), ends_at = start.plusHours(23), deleted = true)
        )

        val events2 = arrayListOf(
            TimetableEvent(siriusId = 5, fullName = "E1", starts_at = start.plusHours(3), ends_at = start.plusHours(8)),
            TimetableEvent(siriusId = 6, fullName = "E2", starts_at = start.plusHours(10), ends_at = start.plusHours(11)),
            TimetableEvent(fullName = "E3", starts_at = start.plusHours(21), ends_at = start.plusHours(22)),
            TimetableEvent(fullName = "E4", starts_at = start.plusHours(13), ends_at = start.plusHours(23), deleted = true)
        )

        assert(events1 == events2)
        assert(listsEqual(events1, events2))

        var changedEvent = TimetableEvent(siriusId = 8, fullName = "E5", starts_at = start.plusHours(3), ends_at = start.plusHours(8))
        events2.add(0, changedEvent)
        assert(events1 != events2)
        assert(!listsEqual(events1, events2))

        events1.add(changedEvent.deepCopy())
        assert(listsEqual(events1, events2))

        changedEvent = events2.first().also { it.changed = true; it.note = "HelloWorld" }
        events2.removeAt(0)
        events2.add(changedEvent)
        assert(!listsEqual(events1, events2))
    }
}