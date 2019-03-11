package cz.budikpet.bachelorwork

import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Named

//@Module
//internal class ContextModule(
//    @get:Provides
//    @get:Named("ApplicationContext")
//    var context: Context
//)

@Module
internal class ContextModule(val context: Context) {

    @Provides
    fun getMyContext(): Context {
        return context
    }
}