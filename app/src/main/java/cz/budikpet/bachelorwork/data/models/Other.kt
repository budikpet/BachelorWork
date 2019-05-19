package cz.budikpet.bachelorwork.data.models


data class PassableStringResource(val resId: Int, val args: List<String>? = null)

data class Counter(val number: Int = 0) {

    fun increment(): Counter {
        return Counter(number + 1)
    }

    fun decrement(): Counter {
        var number = this.number - 1

        if(number < 0)
            number = 0

        return Counter(number)
    }
}