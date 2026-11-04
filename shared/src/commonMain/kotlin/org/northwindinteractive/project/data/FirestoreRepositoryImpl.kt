package org.northwindinteractive.project.data

import com.northwindinteractive.northwindinteractivepapertrader.domain.repository.FirestoreRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirestoreRepositoryImpl(
    private val firestore: FirebaseFirestore
) : FirestoreRepository{

}