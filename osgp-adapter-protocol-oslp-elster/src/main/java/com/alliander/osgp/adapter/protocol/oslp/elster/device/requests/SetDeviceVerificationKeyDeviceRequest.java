/**
 * Copyright 2014-2016 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.elster.device.requests;

import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceRequest;

public class SetDeviceVerificationKeyDeviceRequest extends DeviceRequest {

    private String verificationKey;

    public SetDeviceVerificationKeyDeviceRequest(final String organisationIdentification, final String deviceIdentification,
            final String correlationUid, final String verificationKey) {
        super(organisationIdentification, deviceIdentification, correlationUid);

        this.verificationKey = verificationKey;
    }

    public SetDeviceVerificationKeyDeviceRequest(final String organisationIdentification, final String deviceIdentification,
            final String correlationUid, final String verificationKey, final String domain, final String domainVersion,
            final String messageType, final String ipAddress, final int retryCount, final boolean isScheduled) {
        super(organisationIdentification, deviceIdentification, correlationUid, domain, domainVersion, messageType,
                ipAddress, retryCount, isScheduled);

        this.verificationKey = verificationKey;
    }

    public String getVerificationKey() {
        return this.verificationKey;
    }

}