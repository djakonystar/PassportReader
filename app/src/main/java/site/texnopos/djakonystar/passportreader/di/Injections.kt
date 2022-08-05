package site.texnopos.djakonystar.passportreader.di

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import site.texnopos.djakonystar.passportreader.data.ReadTask
import site.texnopos.djakonystar.passportreader.viewmodel.ReadViewModel

val helperModule = module {
    singleOf(::ReadTask)
}

val viewModelModule = module {
    viewModelOf(::ReadViewModel)
}
