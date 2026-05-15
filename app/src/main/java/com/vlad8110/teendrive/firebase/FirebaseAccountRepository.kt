package com.vlad8110.teendrive.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.vlad8110.teendrive.data.AccountState
import com.vlad8110.teendrive.model.ConnectedTeen
import com.vlad8110.teendrive.model.PairingPayload
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.time.Instant
import java.util.UUID

data class AccountSyncResult(
    val state: AccountState,
    val statusMessage: String,
    val uid: String,
    val fcmToken: String?,
)

data class PairingClaimResult(
    val state: AccountState,
    val connectedTeen: ConnectedTeen,
    val statusMessage: String,
)

class FirebaseAccountRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {
    suspend fun signInIfNeeded(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid
            ?: error("Firebase anonymous sign-in did not return a user.")
    }

    suspend fun fcmTokenOrNull(): String? =
        runCatching { messaging.token.await() }.getOrNull()

    suspend fun syncTeenProfile(state: AccountState): AccountSyncResult {
        return syncTeenProfile(state, allowPermissionRetry = true)
    }

    private suspend fun syncTeenProfile(
        state: AccountState,
        allowPermissionRetry: Boolean,
    ): AccountSyncResult {
        val userId = signInIfNeeded()
        val fcmToken = fcmTokenOrNull()
        val syncState = state.resetTeenCloudLinkIfStale(userId)
        val pairingCode = syncState.pairingCode.ifBlank { makePairingCode() }
        val pairingToken = syncState.pairingToken.ifBlank { makePairingToken() }
        val existingTeenProfile = firestore.collection(TEEN_PROFILES).document(userId).get().await().data.orEmpty()
        val groupId = syncState.familyGroupId
            .ifBlank { preferredExistingFamilyGroupId(userId, existingTeenProfile) }
            .ifBlank { existingTeenProfile["familyGroupID"] as? String ?: "" }
            .ifBlank { firestore.collection(FAMILY_GROUPS).document().id }
        val displayName = syncState.displayName.normalized("Teen")
        val connectedParents = connectedParentsForTeen(existingTeenProfile.stringList("connectedParentIDs"))

        try {
            firestore.collection(FAMILY_GROUPS).document(groupId)
                .set(
                    mapOf(
                        "teenIDs" to FieldValue.arrayUnion(userId),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()

            val profileData = mapOf(
                "familyGroupID" to groupId,
                "displayName" to displayName,
                "fcmToken" to fcmToken,
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            firestore.collection(TEEN_PROFILES).document(userId)
                .set(profileData, SetOptions.merge())
                .await()

            firestore.collection(FAMILY_GROUPS).document(groupId)
                .collection("teens").document(userId)
                .set(profileData, SetOptions.merge())
                .await()

            publishPairingToken(
                familyGroupId = groupId,
                teenProfileId = userId,
                teenName = displayName,
                pairingCode = pairingCode,
                pairingToken = pairingToken,
            )

            registerAndroidClient(userId)
        } catch (exception: FirebaseFirestoreException) {
            if (allowPermissionRetry && exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return syncTeenProfile(state.forceResetTeenCloudLink(), allowPermissionRetry = false)
            }
            throw exception
        }

        return AccountSyncResult(
            state = syncState.copy(
                hasSelectedRole = true,
                displayName = displayName,
                pairingCode = pairingCode,
                pairingToken = pairingToken,
                teenProfileId = userId,
                familyGroupId = groupId,
                connectedParents = connectedParents,
            ),
            statusMessage = "Teen profile synced",
            uid = userId,
            fcmToken = fcmToken,
        )
    }

    private suspend fun connectedParentsForTeen(parentIds: List<String>): Set<String> =
        parentIds.map { parentId ->
            val parentData = firestore.collection(PARENT_PROFILES).document(parentId).get().await().data.orEmpty()
            val parentName = (parentData["displayName"] as? String).orEmpty().ifBlank { "Parent" }
            "${parentId}|${parentName.replace("|", " ")}"
        }.toSet()

    suspend fun fetchConnectedParentNamesForTeen(teenProfileId: String): List<String> {
        if (teenProfileId.isBlank()) return emptyList()
        val teenData = firestore.collection(TEEN_PROFILES).document(teenProfileId).get().await().data.orEmpty()
        return teenData.stringList("connectedParentIDs").map { parentId ->
            val parentData = firestore.collection(PARENT_PROFILES).document(parentId).get().await().data.orEmpty()
            (parentData["displayName"] as? String).orEmpty().ifBlank { "Parent" }
        }.distinct()
    }

    suspend fun syncParentProfile(state: AccountState): AccountSyncResult {
        val userId = signInIfNeeded()
        val fcmToken = fcmTokenOrNull()
        val syncState = state.resetParentCloudLinkIfStale(userId)
        val displayName = syncState.displayName.normalized("Parent")
        val connectedTeens = restoredConnectedTeensForParent(userId, syncState)
        val familyIds = connectedTeens.mapNotNull { it.substringAfterLast("|").takeIf(String::isNotBlank) }.distinct()
        val teenIds = connectedTeens.mapNotNull { encoded ->
            encoded.split("|").getOrNull(3)?.takeIf(String::isNotBlank)
        }.distinct()

        val profileData = mutableMapOf<String, Any?>(
            "displayName" to displayName,
            "fcmToken" to fcmToken,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (familyIds.isNotEmpty() || teenIds.isNotEmpty()) {
            profileData["familyGroupIDs"] = familyIds
            profileData["connectedTeenIDs"] = teenIds
        }

        firestore.collection(PARENT_PROFILES).document(userId)
            .set(profileData, SetOptions.merge())
            .await()

        registerAndroidClient(userId)

        return AccountSyncResult(
            state = syncState.copy(
                hasSelectedRole = true,
                displayName = displayName,
                parentProfileId = userId,
                connectedTeens = connectedTeens,
            ),
            statusMessage = "Parent profile synced",
            uid = userId,
            fcmToken = fcmToken,
        )
    }

    private suspend fun restoredConnectedTeensForParent(
        parentId: String,
        state: AccountState,
    ): Set<String> {
        val localCloudTeens = state.connectedTeens
            .mapNotNull(ConnectedTeen::decode)
            .filter { it.teenProfileId.isNotBlank() && it.familyGroupId.isNotBlank() }
        if (localCloudTeens.isNotEmpty()) {
            return state.connectedTeens
        }

        val parentData = firestore.collection(PARENT_PROFILES).document(parentId).get().await().data.orEmpty()
        val parentFamilyIds = parentData.stringList("familyGroupIDs")
        val familyIds = parentFamilyIds
            .ifEmpty { listOf(state.familyGroupId).filter { it.isNotBlank() } }
            .ifEmpty {
                firestore.collection(FAMILY_GROUPS)
                    .whereArrayContains("parentIDs", parentId)
                    .get()
                    .await()
                    .documents
                    .map { it.id }
            }
            .distinct()
        val teenIds = parentData.stringList("connectedTeenIDs").ifEmpty {
            familyIds.flatMap { familyGroupId ->
                firestore.collection(FAMILY_GROUPS).document(familyGroupId).get().await()
                    .data.orEmpty()
                    .stringList("teenIDs")
            }.distinct()
        }
        if (teenIds.isEmpty()) return state.connectedTeens

        return teenIds.mapNotNull { teenId ->
            val teenData = firestore.collection(TEEN_PROFILES).document(teenId).get().await().data.orEmpty()
            val familyGroupId = (teenData["familyGroupID"] as? String)
                ?: familyIds.singleOrNull()
                ?: familyIds.firstOrNull()
                ?: return@mapNotNull null
            val displayName = (teenData["displayName"] as? String).orEmpty().ifBlank { "Teen" }
            ConnectedTeen(
                id = teenId,
                name = displayName,
                pairingCode = state.pairingCode,
                teenProfileId = teenId,
                familyGroupId = familyGroupId,
            ).encode()
        }.toSet().ifEmpty { state.connectedTeens }
    }

    private suspend fun preferredExistingFamilyGroupId(
        teenId: String,
        teenProfileData: Map<String, Any?>,
    ): String {
        val connectedParentIds = teenProfileData.stringList("connectedParentIDs")
        val linkedFamilyIds = connectedParentIds.flatMap { parentId ->
            firestore.collection(PARENT_PROFILES).document(parentId).get().await()
                .data.orEmpty()
                .stringList("familyGroupIDs")
        }

        linkedFamilyIds
            .map { familyGroupId ->
                familyGroupId to firestore.collection(FAMILY_GROUPS).document(familyGroupId).get().await().data.orEmpty()
            }
            .filter { (_, data) ->
                teenId in data.stringList("teenIDs") && data.stringList("parentIDs").isNotEmpty()
            }
            .maxByOrNull { (_, data) -> data["updatedAt"] as? Timestamp ?: Timestamp(0, 0) }
            ?.first
            ?.let { return it }

        return firestore.collection(FAMILY_GROUPS)
            .whereArrayContains("teenIDs", teenId)
            .get()
            .await()
            .documents
            .map { it.id to it.data.orEmpty() }
            .filter { (_, data) -> data.stringList("parentIDs").isNotEmpty() }
            .maxByOrNull { (_, data) -> data["updatedAt"] as? Timestamp ?: Timestamp(0, 0) }
            ?.first
            .orEmpty()
    }

    suspend fun claimPairingToken(
        state: AccountState,
        scannedPayload: String,
    ): PairingClaimResult {
        val pairing = PairingPayload.parse(scannedPayload) ?: error("That QR code is not a TeenDrive pairing code.")
        require(pairing.teenProfileId.isNotBlank() && pairing.familyGroupId.isNotBlank()) {
            "Teen QR is not cloud-ready. Open Account on the teen phone while online, then scan the new QR."
        }
        require(pairing.token.isNotBlank()) {
            "Teen QR is expired. Generate a new QR code on the teen phone."
        }

        val parentId = signInIfNeeded()
        val parentName = state.displayName.normalized("Parent")
        val fcmToken = fcmTokenOrNull()
        val tokenRef = firestore.collection(FAMILY_GROUPS)
            .document(pairing.familyGroupId)
            .collection("pairingTokens")
            .document(pairing.token)
        val tokenDocument = tokenRef.get().await()
        val tokenData = tokenDocument.data ?: error("Teen QR is no longer valid.")

        require(tokenData["teenID"] == pairing.teenProfileId && tokenData["familyGroupID"] == pairing.familyGroupId) {
            "Teen QR is no longer valid."
        }
        val usedByParentId = tokenData["usedByParentID"] as? String
        require(usedByParentId.isNullOrBlank() || usedByParentId == parentId) {
            "Teen QR was already used. Generate a new QR code."
        }
        val expiresAt = tokenData["expiresAt"] as? Timestamp
        require(expiresAt != null && expiresAt.toInstant().isAfter(Instant.now())) {
            "Teen QR expired. Generate a new QR code."
        }

        tokenRef.set(
            mapOf(
                "usedByParentID" to parentId,
                "usedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        connectParentInFirestore(
            pairing = pairing,
            parentId = parentId,
            parentName = parentName,
            fcmToken = fcmToken,
        )

        val connectedTeen = ConnectedTeen(
            name = pairing.teenName.ifBlank { "Teen" },
            pairingCode = pairing.code,
            teenProfileId = pairing.teenProfileId,
            familyGroupId = pairing.familyGroupId,
        )
        return PairingClaimResult(
            state = state.copy(
                hasSelectedRole = true,
                displayName = parentName,
                parentProfileId = parentId,
                familyGroupId = pairing.familyGroupId,
                connectedTeens = (state.connectedTeens + connectedTeen.encode()).toSet(),
            ),
            connectedTeen = connectedTeen,
            statusMessage = "Teen connected",
        )
    }

    private suspend fun publishPairingToken(
        familyGroupId: String,
        teenProfileId: String,
        teenName: String,
        pairingCode: String,
        pairingToken: String,
    ) {
        firestore.collection(FAMILY_GROUPS)
            .document(familyGroupId)
            .collection("pairingTokens")
            .document(pairingToken)
            .set(
                mapOf(
                    "code" to pairingCode,
                    "teenID" to teenProfileId,
                    "teenName" to teenName,
                    "familyGroupID" to familyGroupId,
                    "createdByTeenID" to teenProfileId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to Timestamp(Date.from(Instant.now().plusSeconds(30 * 60L))),
                    "usedByParentID" to null,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private suspend fun connectParentInFirestore(
        pairing: PairingPayload,
        parentId: String,
        parentName: String,
        fcmToken: String?,
    ) {
        val familyRef = firestore.collection(FAMILY_GROUPS).document(pairing.familyGroupId)
        val parentRef = firestore.collection(PARENT_PROFILES).document(parentId)
        val teenRef = firestore.collection(TEEN_PROFILES).document(pairing.teenProfileId)

        familyRef.set(
            mapOf(
                "parentIDs" to FieldValue.arrayUnion(parentId),
                "teenIDs" to FieldValue.arrayUnion(pairing.teenProfileId),
                "lastPairingToken" to pairing.token,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        parentRef.set(
            mapOf(
                "displayName" to parentName,
                "familyGroupIDs" to FieldValue.arrayUnion(pairing.familyGroupId),
                "connectedTeenIDs" to FieldValue.arrayUnion(pairing.teenProfileId),
                "fcmToken" to fcmToken,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        teenRef.set(
            mapOf(
                "connectedParentIDs" to FieldValue.arrayUnion(parentId),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        familyRef.collection("teens").document(pairing.teenProfileId)
            .set(
                mapOf(
                    "connectedParentIDs" to FieldValue.arrayUnion(parentId),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private suspend fun registerAndroidClient(uid: String) {
        firestore.collection("androidClients").document(uid)
            .set(
                mapOf(
                    "platform" to "android",
                    "uid" to uid,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    companion object {
        private const val FAMILY_GROUPS = "familyGroups"
        private const val TEEN_PROFILES = "teenProfiles"
        private const val PARENT_PROFILES = "parentProfiles"

        fun makePairingCode(): String = (1..6).joinToString("") { (0..9).random().toString() }

        fun makePairingToken(): String = UUID.randomUUID().toString().replace("-", "").lowercase()
    }
}

private fun String.normalized(fallback: String): String = trim().ifBlank { fallback }

private fun Map<String, Any?>.stringList(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().mapNotNull { it as? String }.filter { it.isNotBlank() }


private fun AccountState.resetTeenCloudLinkIfStale(currentUserId: String): AccountState {
    val staleTeenProfile = teenProfileId.isNotBlank() && teenProfileId != currentUserId
    val orphanedFamilyGroup = teenProfileId.isBlank() && familyGroupId.isNotBlank()
    if (!staleTeenProfile && !orphanedFamilyGroup) return this

    return copy(
        familyGroupId = "",
        teenProfileId = "",
        connectedParents = emptySet(),
        pairingCode = FirebaseAccountRepository.makePairingCode(),
        pairingToken = FirebaseAccountRepository.makePairingToken(),
    )
}

private fun AccountState.forceResetTeenCloudLink(): AccountState =
    copy(
        familyGroupId = "",
        teenProfileId = "",
        connectedParents = emptySet(),
        pairingCode = FirebaseAccountRepository.makePairingCode(),
        pairingToken = FirebaseAccountRepository.makePairingToken(),
    )

private fun AccountState.resetParentCloudLinkIfStale(currentUserId: String): AccountState =
    if (parentProfileId.isNotBlank() && parentProfileId != currentUserId) {
        copy(parentProfileId = "")
    } else {
        this
    }
