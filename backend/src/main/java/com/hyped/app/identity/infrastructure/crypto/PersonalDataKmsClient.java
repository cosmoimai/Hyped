package com.hyped.app.identity.infrastructure.crypto;

interface PersonalDataKmsClient {
    byte[] wrap(String kmsKeyName, byte[] plaintextDek, byte[] associatedData);

    byte[] unwrap(String kmsKeyName, byte[] wrappedDek, byte[] associatedData);
}
