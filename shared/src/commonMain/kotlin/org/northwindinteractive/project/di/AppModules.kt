package org.northwindinteractive.project.di

import com.juul.kable.Scanner
import com.northwindinteractive.northwindinteractivepapertrader.domain.repository.FirestoreRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.northwindinteractive.project.data.AuthRepositoryImpl
import org.northwindinteractive.project.data.FirestoreRepositoryImpl
import org.northwindinteractive.project.domain.repository.AuthRepository
import org.northwindinteractive.project.presentation.AuthViewModel

val firebaseModule = module {

    single<FirebaseAuth>{
        Firebase.auth
    }

    single<FirebaseFirestore>{
        Firebase.firestore
    }

    single<FirestoreRepository>{
        FirestoreRepositoryImpl(
            firestore = get()
        )
    }

    single<AuthRepository> {
        AuthRepositoryImpl()
    }
}

val bluetoothModule = module {
    single{
        Scanner()
    }
}

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
}
