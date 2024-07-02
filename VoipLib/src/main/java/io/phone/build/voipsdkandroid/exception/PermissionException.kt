package io.phone.build.voipsdkandroid.exception

class PermissionException internal constructor(missingPermission: String) : Exception(
    "Missing required permission: $missingPermission"
)
