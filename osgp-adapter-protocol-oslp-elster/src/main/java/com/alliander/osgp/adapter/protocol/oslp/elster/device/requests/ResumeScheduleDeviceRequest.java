/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.elster.device.requests;

import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceRequest;
import com.alliander.osgp.dto.valueobjects.ResumeScheduleMessageDataContainerDto;

public class ResumeScheduleDeviceRequest extends DeviceRequest {

    private ResumeScheduleMessageDataContainerDto resumeScheduleContainer;

    public ResumeScheduleDeviceRequest(final String organisationIdentification, final String deviceIdentification,
            final String correlationUid, final ResumeScheduleMessageDataContainerDto resumeScheduleContainer) {
        super(organisationIdentification, deviceIdentification, correlationUid);

        this.resumeScheduleContainer = resumeScheduleContainer;
    }

    public ResumeScheduleDeviceRequest(final String organisationIdentification, final String deviceIdentification,
            final String correlationUid, final ResumeScheduleMessageDataContainerDto resumeScheduleContainer,
            final String domain, final String domainVersion, final String messageType, final String ipAddress,
            final int retryCount, final boolean isScheduled) {
        super(organisationIdentification, deviceIdentification, correlationUid, domain, domainVersion, messageType,
                ipAddress, retryCount, isScheduled);

        this.resumeScheduleContainer = resumeScheduleContainer;
    }

    public ResumeScheduleMessageDataContainerDto getResumeScheduleContainer() {
        return this.resumeScheduleContainer;
    }
}
