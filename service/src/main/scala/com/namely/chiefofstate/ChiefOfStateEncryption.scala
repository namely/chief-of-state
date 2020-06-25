package com.namely.chiefofstate

import com.google.protobuf.any.Any

trait ChiefOfStateEncryption {

  def encrypt(event: Any): Any

  def decrypt(encryptedEvent: Any): Any
}
