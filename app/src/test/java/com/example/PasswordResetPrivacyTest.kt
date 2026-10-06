package com.example

import com.example.data.repository.passwordResetErrorMessage
import org.junit.Assert.*
import org.junit.Test

class PasswordResetPrivacyTest {
  @Test fun missingAndDisabledAccountsReceiveTheSameGenericCompletion() {
    assertNull(passwordResetErrorMessage("ERROR_USER_NOT_FOUND"))
    assertNull(passwordResetErrorMessage("ERROR_USER_DISABLED"))
  }

  @Test fun malformedAddressAndServiceFailuresRemainRetryableErrors() {
    assertNotNull(passwordResetErrorMessage("ERROR_INVALID_EMAIL"))
    assertNotNull(passwordResetErrorMessage("ERROR_NETWORK_REQUEST_FAILED"))
    assertNotNull(passwordResetErrorMessage(null))
  }
}
