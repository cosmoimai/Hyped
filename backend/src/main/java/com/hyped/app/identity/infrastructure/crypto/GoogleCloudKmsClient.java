package com.hyped.app.identity.infrastructure.crypto;

import com.google.cloud.kms.v1.DecryptRequest;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;

final class GoogleCloudKmsClient implements PersonalDataKmsClient {
    private final KeyManagementServiceClient client;

    GoogleCloudKmsClient(KeyManagementServiceClient client) {
        this.client = client;
    }

    @Override
    public byte[] wrap(String kmsKeyName, byte[] plaintextDek, byte[] associatedData) {
        try {
            EncryptRequest request = EncryptRequest.newBuilder()
                    .setName(kmsKeyName)
                    .setPlaintext(ByteString.copyFrom(plaintextDek))
                    .setAdditionalAuthenticatedData(ByteString.copyFrom(associatedData))
                    .build();
            return client.encrypt(request).getCiphertext().toByteArray();
        } catch (RuntimeException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
    }

    @Override
    public byte[] unwrap(String kmsKeyName, byte[] wrappedDek, byte[] associatedData) {
        try {
            DecryptRequest request = DecryptRequest.newBuilder()
                    .setName(kmsKeyName)
                    .setCiphertext(ByteString.copyFrom(wrappedDek))
                    .setAdditionalAuthenticatedData(ByteString.copyFrom(associatedData))
                    .build();
            return client.decrypt(request).getPlaintext().toByteArray();
        } catch (RuntimeException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
    }
}
