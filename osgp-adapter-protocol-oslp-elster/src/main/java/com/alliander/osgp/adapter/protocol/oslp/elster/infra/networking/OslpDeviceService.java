/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.elster.infra.networking;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alliander.osgp.adapter.protocol.oslp.elster.application.mapping.OslpMapper;
import com.alliander.osgp.adapter.protocol.oslp.elster.application.services.oslp.OslpDeviceSettingsService;
import com.alliander.osgp.adapter.protocol.oslp.elster.application.services.oslp.OslpSigningService;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceMessageStatus;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceResponseHandler;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.GetPowerUsageHistoryDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.GetStatusDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.ResumeScheduleDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetConfigurationDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetDeviceVerificationKeyDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetEventNotificationsDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetLightDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetScheduleDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetTransitionDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SwitchConfigurationBankRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SwitchFirmwareDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.UpdateDeviceSslCertificationDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.UpdateFirmwareDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.EmptyDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.GetActualPowerUsageDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.GetConfigurationDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.GetFirmwareVersionDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.GetPowerUsageHistoryDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.GetStatusDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.domain.entities.OslpDevice;
import com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.OslpLogItemRequestMessage;
import com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.OslpLogItemRequestMessageSender;
import com.alliander.osgp.dto.valueobjects.ConfigurationDto;
import com.alliander.osgp.dto.valueobjects.DeviceStatusDto;
import com.alliander.osgp.dto.valueobjects.EventNotificationTypeDto;
import com.alliander.osgp.dto.valueobjects.LightTypeDto;
import com.alliander.osgp.dto.valueobjects.LightValueDto;
import com.alliander.osgp.dto.valueobjects.LinkTypeDto;
import com.alliander.osgp.dto.valueobjects.PageInfoDto;
import com.alliander.osgp.dto.valueobjects.PowerUsageDataDto;
import com.alliander.osgp.dto.valueobjects.PowerUsageHistoryResponseMessageDataContainerDto;
import com.alliander.osgp.dto.valueobjects.RelayMapDto;
import com.alliander.osgp.dto.valueobjects.ScheduleDto;
import com.alliander.osgp.dto.valueobjects.ScheduleMessageDataContainerDto;
import com.alliander.osgp.oslp.Oslp;
import com.alliander.osgp.oslp.Oslp.GetFirmwareVersionRequest;
import com.alliander.osgp.oslp.Oslp.GetStatusRequest;
import com.alliander.osgp.oslp.Oslp.SetScheduleRequest;
import com.alliander.osgp.oslp.OslpEnvelope;
import com.alliander.osgp.shared.exceptionhandling.ComponentType;
import com.alliander.osgp.shared.exceptionhandling.ConnectionFailureException;
import com.google.protobuf.ByteString;

@Component
public class OslpDeviceService implements DeviceService {

    private static final String DATE_FORMAT = "yyyyMMdd";
    private static final String TIME_FORMAT = "HHmmss";
    private static final String DATETIME_FORMAT = DATE_FORMAT + TIME_FORMAT;

    private static final Logger LOGGER = LoggerFactory.getLogger(OslpDeviceService.class);

    @Autowired
    private OslpChannelHandlerClient oslpChannelHandler;

    @Autowired
    private OslpMapper mapper;

    @Resource
    private int oslpPortClient;

    @Resource
    private int oslpPortClientLocal;

    @Resource
    private boolean executeResumeScheduleAfterSetLight;

    @Autowired
    private OslpDeviceSettingsService oslpDeviceSettingsService;

    @Autowired
    private OslpLogItemRequestMessageSender oslpLogItemRequestMessageSender;

    @Autowired
    private OslpSigningService oslpSigningService;

    @Override
    public void startSelfTest(final DeviceRequest deviceRequest) {
        LOGGER.info("startSelfTest() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestStartSelfTest(deviceRequest);
    }

    @Override
    public void doStartSelfTest(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doStartSelfTest() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler responseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseStartSelfTest(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, responseHandler, deviceRequest);
    }

    @Override
    public void stopSelfTest(final DeviceRequest deviceRequest) {
        LOGGER.info("stopSelfTest() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestStopSelfTest(deviceRequest);
    }

    @Override
    public void doStopSelfTest(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doStopSelfTest() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope response) {
                OslpDeviceService.this.handleOslpResponseStopSelfTest(deviceRequest, response, deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void setLight(final SetLightDeviceRequest deviceRequest) {
        LOGGER.info("setLight() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetLight(deviceRequest);
    }

    @Override
    public void doSetLight(final OslpEnvelope oslpRequest, final DeviceRequest setLightdeviceRequest,
            final ResumeScheduleDeviceRequest resumeScheduleDeviceRequest,
            final DeviceResponseHandler setLightDeviceResponseHandler,
            final DeviceResponseHandler resumeScheduleDeviceResponseHandler, final String ipAddress)
            throws IOException {
        LOGGER.info("doSetLight() for device: {}.", setLightdeviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(setLightdeviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetLight(setLightdeviceRequest, resumeScheduleDeviceRequest,
                        oslpResponse, setLightDeviceResponseHandler, resumeScheduleDeviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, setLightdeviceRequest, setLightDeviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, setLightdeviceRequest);
    }

    @Override
    public void setEventNotifications(final SetEventNotificationsDeviceRequest deviceRequest) {
        LOGGER.info("setEventNotifications() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetEventNotifications(deviceRequest);
    }

    @Override
    public void doSetEventNotifications(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doSetEventNotifications() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetEventNotifications(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void updateFirmware(final UpdateFirmwareDeviceRequest deviceRequest) {
        LOGGER.info("updateFirmware() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestUpdateFirmware(deviceRequest);
    }

    @Override
    public void doUpdateFirmware(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doUpdateFirmware() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseUpdateFirmware(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void getFirmwareVersion(final DeviceRequest deviceRequest) {
        LOGGER.info("getFirmwareVersion() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestGetFirmwareVersion(deviceRequest);
    }

    @Override
    public void doGetFirmwareVersion(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doGetFirmwareVersion() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseGetFirmwareVersion(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void switchFirmware(final SwitchFirmwareDeviceRequest deviceRequest) {
        LOGGER.info("switchFirmware() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSwitchFirmware(deviceRequest);
    }

    private void buildOslpRequestSwitchFirmware(final SwitchFirmwareDeviceRequest deviceRequest) {
        final Oslp.SwitchFirmwareRequest switchFirmwareRequest = Oslp.SwitchFirmwareRequest.newBuilder()
                .setNewFirmwareVersion(deviceRequest.getVersion()).build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSwitchFirmwareRequest(switchFirmwareRequest).build(),
                deviceRequest.getVersion());
    }

    @Override
    public void doSwitchFirmware(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {

        LOGGER.info("doSwitchFirmware() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSwitchFirmware(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void updateDeviceSslCertification(final UpdateDeviceSslCertificationDeviceRequest deviceRequest) {
        LOGGER.info("UpdateDeviceSslCertification() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestUpdateDeviceSslCertification(deviceRequest);
    }

    private void buildOslpRequestUpdateDeviceSslCertification(
            final UpdateDeviceSslCertificationDeviceRequest deviceRequest) {
        final Oslp.UpdateDeviceSslCertificationRequest updateDeviceSslCertificationRequest = Oslp.UpdateDeviceSslCertificationRequest
                .newBuilder().setCertificateDomain(deviceRequest.getCertification().getCertificateDomain())
                .setCertificateUrl(deviceRequest.getCertification().getCertificateUrl()).build();

        this.buildAndSignEnvelope(
                deviceRequest, Oslp.Message.newBuilder()
                        .setUpdateDeviceSslCertificationRequest(updateDeviceSslCertificationRequest).build(),
                deviceRequest.getCertification());

    }

    @Override
    public void doUpdateDeviceSslCertification(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {

        LOGGER.info("doUpdateDeviceSslCertification() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseUpdateDeviceSslCertification(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void setDeviceVerificationKey(final SetDeviceVerificationKeyDeviceRequest deviceRequest) {
        LOGGER.info("SetDeviceVerificationKey() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetDeviceVerificationKey(deviceRequest);
    }

    @Override
    public void doSetDeviceVerificationKey(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {

        LOGGER.info("doSetDeviceVerificationKey() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetDeviceVerificationKey(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void setSchedule(final SetScheduleDeviceRequest deviceRequest) {
        LOGGER.info("setSchedule() for device: {}.", deviceRequest.getDeviceIdentification());

        final int pageSize = 5;
        final int numberOfPages = (int) Math
                .ceil((double) deviceRequest.getScheduleMessageDataContainer().getScheduleList().size() / pageSize);

        if (numberOfPages == 1) {
            this.processOslpRequestSetScheduleSingle(deviceRequest);
        } else {
            final Pager pager = new Pager(deviceRequest.getScheduleMessageDataContainer().getScheduleList().size(),
                    pageSize);

            this.processOslpRequestSetSchedulePaged(deviceRequest, pager);
        }
    }

    @Override
    public void doSetSchedule(final OslpEnvelope oslpRequest, final SetScheduleDeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress, final String domain,
            final String domainVersion, final String messageType, final int retryCount, final boolean isScheduled,
            final PageInfoDto pageInfo) throws IOException {
        LOGGER.info("doSetSchedule() for device: {}.", deviceRequest.getDeviceIdentification());

        if (pageInfo == null) {
            this.doProcessOslpRequestSetScheduleSingle(oslpRequest, deviceRequest, deviceResponseHandler, ipAddress);
        } else {
            final Pager pager = new Pager(deviceRequest.getScheduleMessageDataContainer().getScheduleList().size(), 5);
            pager.setCurrentPage(pageInfo.getCurrentPage());
            pager.setNumberOfPages(pageInfo.getTotalPages());
            this.doProcessOslpRequestSetSchedulePaged(oslpRequest, deviceRequest, deviceResponseHandler, ipAddress,
                    domain, domainVersion, messageType, retryCount, isScheduled, pager);
        }
    }

    private void processOslpRequestSetScheduleSingle(final SetScheduleDeviceRequest deviceRequest) {

        LOGGER.debug("Processing single set schedule request for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetScheduleSingle(deviceRequest);
    }

    private void doProcessOslpRequestSetScheduleSingle(final OslpEnvelope oslpRequest,
            final SetScheduleDeviceRequest deviceRequest, final DeviceResponseHandler deviceResponseHandler,
            final String ipAddress) throws IOException {

        LOGGER.debug("Processing single set schedule request for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetScheduleSingle(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    protected void handleException(final Throwable t, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler) {

        final DeviceResponse deviceResponse = new DeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid());

        if (t instanceof IOException) {
            // Replace t by an OSGP Exception
            final ConnectionFailureException ex = new ConnectionFailureException(ComponentType.PROTOCOL_OSLP,
                    "Connection failure");
            deviceResponseHandler.handleException(ex, deviceResponse);
        } else {
            deviceResponseHandler.handleException(t, deviceResponse);
        }
    }

    private void handleOslpResponseSetScheduleSingle(final SetScheduleDeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetScheduleResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetScheduleResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void buildOslpRequestSetScheduleSingle(final SetScheduleDeviceRequest deviceRequest) {
        final List<Oslp.Schedule> oslpSchedules = this
                .convertToOslpSchedules(deviceRequest.getScheduleMessageDataContainer().getScheduleList());

        final Oslp.SetScheduleRequest.Builder request = SetScheduleRequest.newBuilder().addAllSchedules(oslpSchedules)
                .setScheduleType(
                        this.mapper.map(deviceRequest.getRelayType(), com.alliander.osgp.oslp.Oslp.RelayType.class));

        final ScheduleMessageDataContainerDto scheduleMessageDataContainer = new ScheduleMessageDataContainerDto(
                deviceRequest.getScheduleMessageDataContainer().getScheduleList());

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetScheduleRequest(request.build()).build(), scheduleMessageDataContainer);
    }

    private void processOslpRequestSetSchedulePaged(final SetScheduleDeviceRequest deviceRequest, final Pager pager) {
        LOGGER.debug("Processing paged set schedule request for device: {}, page {} of {}",
                deviceRequest.getDeviceIdentification(), pager.getCurrentPage(), pager.numberOfPages);

        this.buildOslpRequestSetSchedulePaged(deviceRequest, pager);
    }

    private void doProcessOslpRequestSetSchedulePaged(final OslpEnvelope oslpRequest,
            final SetScheduleDeviceRequest deviceRequest, final DeviceResponseHandler deviceResponseHandler,
            final String ipAddress, final String domain, final String domainVersion, final String messageType,
            final int retryCount, final boolean isScheduled, final Pager pager) throws IOException {
        LOGGER.debug("Processing paged set schedule request for device: {}, page {} of {}",
                deviceRequest.getDeviceIdentification(), pager.getCurrentPage(), pager.numberOfPages);

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetSchedulePaged(deviceRequest, oslpResponse, domain,
                        domainVersion, messageType, retryCount, isScheduled, pager, deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    private void handleOslpResponseSetSchedulePaged(final SetScheduleDeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final String domain, final String domainVersion, final String messageType,
            final int retryCount, final boolean isScheduled, final Pager pager,
            final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        // Get response status
        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetScheduleResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetScheduleResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        if (pager.isLastPage() || status != DeviceMessageStatus.OK) {
            // Stop processing pages and handle device response.
            this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

            final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                    deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
            deviceResponseHandler.handleResponse(deviceResponse);
        } else {
            // Process next page
            pager.nextPage();
            this.processOslpRequestSetSchedulePaged(deviceRequest, pager);
        }
    }

    private void buildOslpRequestSetSchedulePaged(final SetScheduleDeviceRequest deviceRequest, final Pager pager) {

        final List<Oslp.Schedule> oslpSchedules = this.convertToOslpSchedules(deviceRequest
                .getScheduleMessageDataContainer().getScheduleList().subList(pager.getIndexFrom(), pager.getIndexTo()));

        final Oslp.SetScheduleRequest.Builder oslpRequestBuilder = SetScheduleRequest.newBuilder()
                .addAllSchedules(oslpSchedules)
                .setScheduleType(
                        this.mapper.map(deviceRequest.getRelayType(), com.alliander.osgp.oslp.Oslp.RelayType.class))
                .setPageInfo(Oslp.PageInfo.newBuilder().setCurrentPage(pager.getCurrentPage())
                        .setPageSize(pager.getPageSize()).setTotalPages(pager.getNumberOfPages()));

        final PageInfoDto pageInfo = new PageInfoDto(pager.getCurrentPage(), pager.getPageSize(),
                pager.getNumberOfPages());
        final ScheduleMessageDataContainerDto scheduleMessageDataContainer = new ScheduleMessageDataContainerDto(
                deviceRequest.getScheduleMessageDataContainer().getScheduleList());
        scheduleMessageDataContainer.setPageInfo(pageInfo);

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetScheduleRequest(oslpRequestBuilder.build()).build(),
                scheduleMessageDataContainer);
    }

    // 1-based pager
    private static class Pager {
        private int currentPage = 1;
        private int itemCount = 0;
        private int pageSize = 5;
        private int numberOfPages = 1;

        public Pager() {
            // Default constructor.
        }

        public Pager(final int itemCount, final int pageSize) {
            this.itemCount = itemCount;
            this.pageSize = pageSize;
            this.numberOfPages = (int) Math.ceil((double) itemCount / (double) pageSize);
        }

        public Pager(final int numberOfPages, final int pageSize, final int currentPage) {
            this.numberOfPages = numberOfPages;
            this.pageSize = pageSize;
            this.currentPage = currentPage;
        }

        public int getCurrentPage() {
            return this.currentPage;
        }

        public int getIndexFrom() {
            return this.pageSize * (this.currentPage - 1);
        }

        public int getIndexTo() {
            return Math.min(this.pageSize * this.currentPage, this.itemCount);
        }

        public int getPageSize() {
            return this.pageSize;
        }

        public int getNumberOfPages() {
            return this.numberOfPages;
        }

        public void setNumberOfPages(final int numberOfPages) {
            this.numberOfPages = numberOfPages;
        }

        public void nextPage() {
            this.currentPage++;
        }

        public boolean isLastPage() {
            return this.currentPage == this.numberOfPages;
        }

        public void setCurrentPage(final int currentPage) {
            this.currentPage = currentPage;
        }
    }

    private List<Oslp.Schedule> convertToOslpSchedules(final List<ScheduleDto> schedules) {
        final List<Oslp.Schedule> oslpSchedules = new ArrayList<>();

        for (final ScheduleDto schedule : schedules) {
            Oslp.Schedule.Builder scheduleBuilder = Oslp.Schedule.newBuilder()
                    .setWeekday(Oslp.Weekday.valueOf(schedule.getWeekDay().ordinal() + 1))
                    .setActionTime(Oslp.ActionTime.valueOf(schedule.getActionTime().ordinal() + 1));

            if (schedule.getStartDay() != null) {
                scheduleBuilder = scheduleBuilder.setStartDay(schedule.getStartDay().toString(DATE_FORMAT));
            }

            if (schedule.getEndDay() != null) {
                scheduleBuilder = scheduleBuilder.setEndDay(schedule.getEndDay().toString(DATE_FORMAT));
            }

            if (StringUtils.isNotBlank(schedule.getTime())) {
                scheduleBuilder = scheduleBuilder.setTime(LocalTime.parse(schedule.getTime()).toString(TIME_FORMAT));
            }

            if (schedule.getTriggerWindow() != null) {
                scheduleBuilder = scheduleBuilder.setWindow(
                        Oslp.Window.newBuilder().setMinutesBefore((int) schedule.getTriggerWindow().getMinutesBefore())
                                .setMinutesAfter((int) schedule.getTriggerWindow().getMinutesAfter()));
            }

            for (final LightValueDto lightValue : schedule.getLightValue()) {
                scheduleBuilder.addValue(this.buildLightValue(lightValue));
            }

            if (schedule.getTriggerType() != null) {
                scheduleBuilder.setTriggerType(Oslp.TriggerType.valueOf(schedule.getTriggerType().ordinal() + 1));
            }

            if (schedule.getIndex() != null) {
                scheduleBuilder.setIndex(schedule.getIndex());
            }

            if (schedule.getIsEnabled() != null) {
                scheduleBuilder.setIsEnabled(schedule.getIsEnabled());
            }

            if (schedule.getMinimumLightsOn() != null) {
                scheduleBuilder.setMinimumLightsOn(schedule.getMinimumLightsOn());
            }

            oslpSchedules.add(scheduleBuilder.build());
        }
        return oslpSchedules;
    }

    @Override
    public void setConfiguration(final SetConfigurationDeviceRequest deviceRequest) {
        LOGGER.info("setConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetConfiguration(deviceRequest);
    }

    @Override
    public void doSetConfiguration(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doSetConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetConfiguration(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void getConfiguration(final DeviceRequest deviceRequest) {
        LOGGER.info("getConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestGetConfiguration(deviceRequest);
    }

    @Override
    public void doGetConfiguration(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doGetConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseGetConfiguration(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void switchConfiguration(final SwitchConfigurationBankRequest deviceRequest) {
        LOGGER.info("switchConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSwitchConfiguration(deviceRequest);
    }

    @Override
    public void doSwitchConfiguration(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doSwitchConfiguration() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSwitchConfiguration(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void getActualPowerUsage(final DeviceRequest deviceRequest) {
        LOGGER.info("getActualPowerUsage() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestGetActualPowerUsage(deviceRequest);
    }

    @Override
    public void doGetActualPowerUsage(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doGetActualPowerUsage() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseGetActualPowerUsage(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void getPowerUsageHistory(final GetPowerUsageHistoryDeviceRequest deviceRequest) {
        LOGGER.info("getPowerUsageHistory() for device: {}.", deviceRequest.getDeviceIdentification());

        final Pager pager = new Pager();
        final List<PowerUsageDataDto> powerUsageHistoryData = new ArrayList<>();

        this.buildOslpRequestGetPowerUsageHistory(deviceRequest, pager, powerUsageHistoryData);
    }

    @Override
    public void doGetPowerUsageHistory(final OslpEnvelope oslpRequest,
            final PowerUsageHistoryResponseMessageDataContainerDto powerUsageHistoryResponseMessageDataContainer,
            final GetPowerUsageHistoryDeviceRequest deviceRequest, final DeviceResponseHandler deviceResponseHandler,
            final String ipAddress, final String domain, final String domainVersion, final String messageType,
            final int retryCount, final boolean isScheduled) throws IOException {
        LOGGER.info("doGetPowerUsageHistory() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final List<PowerUsageDataDto> powerUsageHistoryData = powerUsageHistoryResponseMessageDataContainer
                .getPowerUsageData();
        final PageInfoDto pageInfo = powerUsageHistoryResponseMessageDataContainer.getPageInfo();
        final Pager pager = new Pager(pageInfo.getTotalPages(), pageInfo.getPageSize(), pageInfo.getCurrentPage());

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseGetPowerUsageHistory(deviceRequest, oslpResponse, pager,
                        powerUsageHistoryData, deviceResponseHandler, ipAddress, domain, domainVersion, messageType,
                        retryCount, isScheduled);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    private void processOslpRequestGetPowerUsageHistory(final GetPowerUsageHistoryDeviceRequest deviceRequest,
            final Pager pager, final List<PowerUsageDataDto> powerUsageHistoryData,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress, final String domain,
            final String domainVersion, final String messageType, final int retryCount, final boolean isScheduled)
            throws IOException {
        LOGGER.info("GetPowerUsageHistory() for device: {}, page: {}", deviceRequest.getDeviceIdentification(),
                pager.getCurrentPage());
        LOGGER.debug("deviceResponseHandler is not used in this function: {}", deviceResponseHandler.toString());

        this.buildOslpRequestGetPowerUsageHistory(deviceRequest, pager, powerUsageHistoryData);
    }

    private void handleOslpResponseGetPowerUsageHistory(final GetPowerUsageHistoryDeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final Pager pager, final List<PowerUsageDataDto> powerUsageHistoryData,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress, final String domain,
            final String domainVersion, final String messageType, final int retryCount, final boolean isScheduled) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        // Get response status
        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasGetPowerUsageHistoryResponse()) {
            final Oslp.GetPowerUsageHistoryResponse getPowerUsageHistoryResponse = oslpResponse.getPayloadMessage()
                    .getGetPowerUsageHistoryResponse();
            status = this.mapper.map(getPowerUsageHistoryResponse.getStatus(), DeviceMessageStatus.class);
            powerUsageHistoryData.addAll(this.mapper.mapAsList(getPowerUsageHistoryResponse.getPowerUsageDataList(),
                    PowerUsageDataDto.class));

            if (pager.getNumberOfPages() == 1 && getPowerUsageHistoryResponse.hasPageInfo()) {
                pager.setNumberOfPages(getPowerUsageHistoryResponse.getPageInfo().getTotalPages());
            }

        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        if (pager.isLastPage() || status != DeviceMessageStatus.OK) {
            // Stop processing pages and handle device response.
            this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

            final GetPowerUsageHistoryDeviceResponse deviceResponse = new GetPowerUsageHistoryDeviceResponse(
                    deviceRequest.getOrganisationIdentification(), deviceRequest.getDeviceIdentification(),
                    deviceRequest.getCorrelationUid(), status, powerUsageHistoryData);
            deviceResponseHandler.handleResponse(deviceResponse);

        } else {
            // Process next page
            pager.nextPage();
            try {
                this.processOslpRequestGetPowerUsageHistory(deviceRequest, pager, powerUsageHistoryData,
                        deviceResponseHandler, ipAddress, domain, domainVersion, messageType, retryCount, isScheduled);
            } catch (final IOException e) {
                LOGGER.error("IOException", e);
                final GetPowerUsageHistoryDeviceResponse deviceResponse = new GetPowerUsageHistoryDeviceResponse(
                        deviceRequest.getOrganisationIdentification(), deviceRequest.getDeviceIdentification(),
                        deviceRequest.getCorrelationUid(), DeviceMessageStatus.FAILURE, null);
                deviceResponseHandler.handleResponse(deviceResponse);
            }
        }
    }

    private void buildOslpRequestGetPowerUsageHistory(final GetPowerUsageHistoryDeviceRequest deviceRequest,
            final Pager pager, final List<PowerUsageDataDto> powerUsageHistoryData) {
        final Oslp.HistoryTermType oslpHistoryTermType = this.mapper
                .map(deviceRequest.getPowerUsageHistoryContainer().getHistoryTermType(), Oslp.HistoryTermType.class);
        final Oslp.TimePeriod.Builder oslpTimePeriodBuilder = Oslp.TimePeriod.newBuilder();
        final String startTime = deviceRequest.getPowerUsageHistoryContainer().getTimePeriod().getStartTime()
                .toDateTime(DateTimeZone.UTC).toString(DATETIME_FORMAT);
        final String endTime = deviceRequest.getPowerUsageHistoryContainer().getTimePeriod().getEndTime()
                .toDateTime(DateTimeZone.UTC).toString(DATETIME_FORMAT);

        final Oslp.GetPowerUsageHistoryRequest getPowerUsageHistoryRequest = Oslp.GetPowerUsageHistoryRequest
                .newBuilder().setTimePeriod(oslpTimePeriodBuilder.setStartTime(startTime).setEndTime(endTime))
                .setTermType(oslpHistoryTermType).setPage(pager.getCurrentPage()).build();

        final PowerUsageHistoryResponseMessageDataContainerDto powerUsageHistoryResponseMessageDataContainer = new PowerUsageHistoryResponseMessageDataContainerDto(
                powerUsageHistoryData);
        final PageInfoDto pageInfo = new PageInfoDto(pager.getCurrentPage(), pager.getPageSize(),
                pager.getNumberOfPages());
        powerUsageHistoryResponseMessageDataContainer.setPageInfo(pageInfo);
        powerUsageHistoryResponseMessageDataContainer
                .setStartTime(deviceRequest.getPowerUsageHistoryContainer().getTimePeriod().getStartTime());
        powerUsageHistoryResponseMessageDataContainer
                .setEndTime(deviceRequest.getPowerUsageHistoryContainer().getTimePeriod().getEndTime());
        powerUsageHistoryResponseMessageDataContainer
                .setHistoryTermType(deviceRequest.getPowerUsageHistoryContainer().getHistoryTermType());
        powerUsageHistoryResponseMessageDataContainer
                .setRequestContainer(deviceRequest.getPowerUsageHistoryContainer());

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setGetPowerUsageHistoryRequest(getPowerUsageHistoryRequest).build(),
                powerUsageHistoryResponseMessageDataContainer);
    }

    @Override
    public void getStatus(final GetStatusDeviceRequest deviceRequest) {
        LOGGER.info("getStatus() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestGetStatus(deviceRequest);
    }

    @Override
    public void doGetStatus(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doGetStatus() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseGetStatus(deviceRequest, oslpResponse, deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void resumeSchedule(final ResumeScheduleDeviceRequest deviceRequest) {
        LOGGER.info("resumeSchedule() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestResumeSchedule(deviceRequest);
    }

    @Override
    public void doResumeSchedule(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doResumeSchedule() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseResumeSchedule(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void setReboot(final DeviceRequest deviceRequest) {
        LOGGER.info("setReboot() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetReboot(deviceRequest);
    }

    @Override
    public void doSetReboot(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doSetReboot() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetReboot(deviceRequest, oslpResponse, deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    @Override
    public void setTransition(final SetTransitionDeviceRequest deviceRequest) {
        LOGGER.info("setTranistion() for device: {}.", deviceRequest.getDeviceIdentification());

        this.buildOslpRequestSetTransition(deviceRequest);
    }

    @Override
    public void doSetTransition(final OslpEnvelope oslpRequest, final DeviceRequest deviceRequest,
            final DeviceResponseHandler deviceResponseHandler, final String ipAddress) throws IOException {
        LOGGER.info("doSetTranistion() for device: {}.", deviceRequest.getDeviceIdentification());

        this.saveOslpRequestLogEntry(deviceRequest, oslpRequest);

        final OslpResponseHandler oslpResponseHandler = new OslpResponseHandler() {

            @Override
            public void handleResponse(final OslpEnvelope oslpResponse) {
                OslpDeviceService.this.handleOslpResponseSetTransition(deviceRequest, oslpResponse,
                        deviceResponseHandler);
            }

            @Override
            public void handleException(final Throwable t) {
                OslpDeviceService.this.handleException(t, deviceRequest, deviceResponseHandler);
            }
        };

        this.sendMessage(ipAddress, oslpRequest, oslpResponseHandler, deviceRequest);
    }

    private DeviceResponse buildDeviceResponseGetActualPowerUsage(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        PowerUsageDataDto actualPowerUsageData = null;
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasGetActualPowerUsageResponse()) {
            final Oslp.GetActualPowerUsageResponse response = oslpResponse.getPayloadMessage()
                    .getGetActualPowerUsageResponse();
            actualPowerUsageData = this.mapper.map(response.getPowerUsageData(), PowerUsageDataDto.class);
            status = this.mapper.map(response.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new GetActualPowerUsageDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status,
                actualPowerUsageData);
    }

    private DeviceResponse buildDeviceResponseGetConfiguration(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        ConfigurationDto configuration = null;
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasGetConfigurationResponse()) {
            final Oslp.GetConfigurationResponse getConfigurationResponse = oslpResponse.getPayloadMessage()
                    .getGetConfigurationResponse();
            configuration = this.mapper.map(getConfigurationResponse, ConfigurationDto.class);
            status = this.mapper.map(getConfigurationResponse.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new GetConfigurationDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status, configuration);
    }

    private DeviceResponse buildDeviceResponseSwitchConfiguration(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasSwitchConfigurationResponse()) {
            final Oslp.SwitchConfigurationResponse switchConfigurationResponse = oslpResponse.getPayloadMessage()
                    .getSwitchConfigurationResponse();
            status = this.mapper.map(switchConfigurationResponse.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
    }

    private DeviceResponse buildDeviceResponseSwitchFirmware(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasSwitchFirmwareResponse()) {
            final Oslp.SwitchFirmwareResponse switchFirmwareResponse = oslpResponse.getPayloadMessage()
                    .getSwitchFirmwareResponse();
            status = this.mapper.map(switchFirmwareResponse.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
    }

    private DeviceResponse buildDeviceResponseUpdateDeviceSslCertification(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasUpdateDeviceSslCertificationResponse()) {
            final Oslp.UpdateDeviceSslCertificationResponse updateDeviceSslCertificationResponse = oslpResponse
                    .getPayloadMessage().getUpdateDeviceSslCertificationResponse();

            status = this.mapper.map(updateDeviceSslCertificationResponse.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
    }

    private DeviceResponse buildDeviceResponseSetDeviceVerificationKey(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse) {
        DeviceMessageStatus status = null;

        if (oslpResponse.getPayloadMessage().hasSetDeviceVerificationKeyResponse()) {
            final Oslp.SetDeviceVerificationKeyResponse setDeviceVerificationKeyResponse = oslpResponse
                    .getPayloadMessage().getSetDeviceVerificationKeyResponse();

            status = this.mapper.map(setDeviceVerificationKeyResponse.getStatus(), DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        return new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
    }

    private void buildOslpRequestGetActualPowerUsage(final DeviceRequest deviceRequest) {
        final Oslp.GetActualPowerUsageRequest getActualPowerUsageRequest = Oslp.GetActualPowerUsageRequest.newBuilder()
                .build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setGetActualPowerUsageRequest(getActualPowerUsageRequest).build(), null);
    }

    private void buildOslpRequestGetConfiguration(final DeviceRequest deviceRequest) {
        final Oslp.GetConfigurationRequest getConfigurationRequest = Oslp.GetConfigurationRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setGetConfigurationRequest(getConfigurationRequest).build(), null);
    }

    private void buildOslpRequestSwitchConfiguration(final SwitchConfigurationBankRequest deviceRequest) {
        final Oslp.SwitchConfigurationRequest switchConfigurationRequest = Oslp.SwitchConfigurationRequest.newBuilder()
                .setNewConfigurationSet(ByteString.copyFrom(deviceRequest.getConfigurationBank().getBytes())).build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSwitchConfigurationRequest(switchConfigurationRequest).build(),
                deviceRequest.getConfigurationBank());
    }

    private void buildOslpRequestGetFirmwareVersion(final DeviceRequest deviceRequest) {
        final Oslp.GetFirmwareVersionRequest getFirmwareVersionRequest = GetFirmwareVersionRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setGetFirmwareVersionRequest(getFirmwareVersionRequest).build(), null);
    }

    private void buildOslpRequestGetStatus(final DeviceRequest deviceRequest) {
        final Oslp.GetStatusRequest getStatusRequest = GetStatusRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setGetStatusRequest(getStatusRequest).build(), null);
    }

    private void buildOslpRequestResumeSchedule(final ResumeScheduleDeviceRequest deviceRequest) {
        final Oslp.ResumeScheduleRequest.Builder resumeScheduleRequestBuilder = Oslp.ResumeScheduleRequest.newBuilder();
        if (deviceRequest.getResumeScheduleContainer().getIndex() != null) {
            resumeScheduleRequestBuilder.setIndex(ByteString
                    .copyFrom(new byte[] { deviceRequest.getResumeScheduleContainer().getIndex().byteValue() }));

        }
        resumeScheduleRequestBuilder.setImmediate(deviceRequest.getResumeScheduleContainer().isImmediate());

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setResumeScheduleRequest(resumeScheduleRequestBuilder.build()).build(),
                deviceRequest.getResumeScheduleContainer());
    }

    private void buildOslpRequestSetConfiguration(final SetConfigurationDeviceRequest deviceRequest) {
        // First, sort the relay mapping on (internal) index number (FLEX-2514)
        if (deviceRequest.getConfiguration().getRelayConfiguration() != null) {
            Collections.sort(deviceRequest.getConfiguration().getRelayConfiguration().getRelayMap(),
                    new Comparator<RelayMapDto>() {
                        @Override
                        public int compare(final RelayMapDto o1, final RelayMapDto o2) {
                            return o1.getIndex().compareTo(o2.getIndex());
                        }
                    });
        }

        final Oslp.SetConfigurationRequest setConfigurationRequest = this.mapper.map(deviceRequest.getConfiguration(),
                Oslp.SetConfigurationRequest.class);

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetConfigurationRequest(setConfigurationRequest).build(),
                deviceRequest.getConfiguration());
    }

    private void buildOslpRequestSetEventNotifications(final SetEventNotificationsDeviceRequest deviceRequest) {
        final Oslp.SetEventNotificationsRequest.Builder builder = Oslp.SetEventNotificationsRequest.newBuilder();

        int bitMask = 0;
        for (final EventNotificationTypeDto ent : deviceRequest.getEventNotificationsContainer()
                .getEventNotifications()) {
            bitMask += ent.getValue();
        }

        builder.setNotificationMask(bitMask);

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetEventNotificationsRequest(builder.build()).build(),
                deviceRequest.getEventNotificationsContainer());
    }

    private void buildOslpRequestSetLight(final SetLightDeviceRequest deviceRequest) {
        final Oslp.SetLightRequest.Builder setLightRequestBuilder = Oslp.SetLightRequest.newBuilder();

        for (final LightValueDto lightValue : deviceRequest.getLightValuesContainer().getLightValues()) {
            setLightRequestBuilder.addValues(this.buildLightValue(lightValue));
        }

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetLightRequest(setLightRequestBuilder.build()).build(),
                deviceRequest.getLightValuesContainer());
    }

    private void buildOslpRequestSetReboot(final DeviceRequest deviceRequest) {
        final Oslp.SetRebootRequest setRebootRequest = Oslp.SetRebootRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetRebootRequest(setRebootRequest).build(), null);
    }

    private void buildOslpRequestSetTransition(final SetTransitionDeviceRequest deviceRequest) {
        final Oslp.SetTransitionRequest.Builder setTransitionBuilder = Oslp.SetTransitionRequest.newBuilder()
                .setTransitionType(this.mapper.map(deviceRequest.getTransitionTypeContainer().getTransitionType(),
                        com.alliander.osgp.oslp.Oslp.TransitionType.class));
        if (deviceRequest.getTransitionTypeContainer().getDateTime() != null) {
            setTransitionBuilder
                    .setTime(deviceRequest.getTransitionTypeContainer().getDateTime().toString(TIME_FORMAT));
        }

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetTransitionRequest(setTransitionBuilder.build()).build(),
                deviceRequest.getTransitionTypeContainer());
    }

    private void buildOslpRequestStartSelfTest(final DeviceRequest deviceRequest) {
        final Oslp.StartSelfTestRequest startSelftestRequest = Oslp.StartSelfTestRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setStartSelfTestRequest(startSelftestRequest).build(), null);
    }

    private void buildOslpRequestStopSelfTest(final DeviceRequest deviceRequest) {
        final Oslp.StopSelfTestRequest stopSelftestRequest = Oslp.StopSelfTestRequest.newBuilder().build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setStopSelfTestRequest(stopSelftestRequest).build(), null);
    }

    private void buildOslpRequestUpdateFirmware(final UpdateFirmwareDeviceRequest deviceRequest) {
        final Oslp.UpdateFirmwareRequest updateFirmwareRequest = Oslp.UpdateFirmwareRequest.newBuilder()
                .setFirmwareDomain(deviceRequest.getFirmwareDomain()).setFirmwareUrl(deviceRequest.getFirmwareUrl())
                .build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setUpdateFirmwareRequest(updateFirmwareRequest).build(), null);
    }

    private void buildOslpRequestSetDeviceVerificationKey(final SetDeviceVerificationKeyDeviceRequest deviceRequest) {
        final Oslp.SetDeviceVerificationKeyRequest setDeviceVerificationKey = Oslp.SetDeviceVerificationKeyRequest
                .newBuilder().setCertificateChunk(ByteString.copyFrom(deviceRequest.getVerificationKey().getBytes()))
                .build();

        this.buildAndSignEnvelope(deviceRequest,
                Oslp.Message.newBuilder().setSetDeviceVerificationKeyRequest(setDeviceVerificationKey).build(),
                deviceRequest.getVerificationKey());
    }

    private void handleOslpResponseGetActualPowerUsage(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseGetActualPowerUsage(deviceRequest, oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseGetConfiguration(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseGetConfiguration(deviceRequest, oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSwitchConfiguration(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseSwitchConfiguration(deviceRequest, oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseGetFirmwareVersion(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        String firmwareVersion = "";

        if (oslpResponse.getPayloadMessage().hasGetFirmwareVersionResponse()) {
            firmwareVersion = oslpResponse.getPayloadMessage().getGetFirmwareVersionResponse().getFirmwareVersion();
        }

        final DeviceResponse deviceResponse = new GetFirmwareVersionDeviceResponse(
                deviceRequest.getOrganisationIdentification(), deviceRequest.getDeviceIdentification(),
                deviceRequest.getCorrelationUid(), firmwareVersion);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSwitchFirmware(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseSwitchFirmware(deviceRequest, oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseUpdateDeviceSslCertification(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseUpdateDeviceSslCertification(deviceRequest,
                oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSetDeviceVerificationKey(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        final DeviceResponse deviceResponse = this.buildDeviceResponseSetDeviceVerificationKey(deviceRequest,
                oslpResponse);

        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseGetStatus(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceStatusDto deviceStatus = null;

        if (oslpResponse.getPayloadMessage().hasGetStatusResponse()) {
            final Oslp.GetStatusResponse getStatusResponse = oslpResponse.getPayloadMessage().getGetStatusResponse();
            final Oslp.Status oslpStatus = getStatusResponse.getStatus();
            if (oslpStatus == Oslp.Status.OK) {
                // Required properties.
                final List<LightValueDto> lightValues = this.mapper.mapAsList(getStatusResponse.getValueList(),
                        LightValueDto.class);
                final LinkTypeDto preferredType = getStatusResponse.getPreferredLinktype()
                        .equals(Oslp.LinkType.LINK_NOT_SET) ? null
                                : this.mapper.map(getStatusResponse.getPreferredLinktype(), LinkTypeDto.class);
                final LinkTypeDto actualLinkType = getStatusResponse.getActualLinktype()
                        .equals(Oslp.LinkType.LINK_NOT_SET) ? null
                                : this.mapper.map(getStatusResponse.getActualLinktype(), LinkTypeDto.class);
                final LightTypeDto lightType = getStatusResponse.getLightType().equals(Oslp.LightType.LT_NOT_SET) ? null
                        : this.mapper.map(getStatusResponse.getLightType(), LightTypeDto.class);
                final int eventNotificationMask = getStatusResponse.getEventNotificationMask();

                deviceStatus = new DeviceStatusDto(lightValues, preferredType, actualLinkType, lightType,
                        eventNotificationMask);

                // Optional properties.
                if (getStatusResponse.hasBootLoaderVersion()) {
                    deviceStatus.setBootLoaderVersion(getStatusResponse.getBootLoaderVersion());
                }
                if (getStatusResponse.getCurrentConfigurationBackUsed() != null
                        && getStatusResponse.getCurrentConfigurationBackUsed().toByteArray().length == 1) {
                    deviceStatus.setCurrentConfigurationBackUsed(this
                            .convertCurrentConfigurationBankUsed(getStatusResponse.getCurrentConfigurationBackUsed()));
                }
                if (getStatusResponse.hasCurrentIp()) {
                    deviceStatus.setCurrentIp(getStatusResponse.getCurrentIp());
                }
                if (getStatusResponse.hasCurrentTime()) {
                    deviceStatus.setCurrentTime(getStatusResponse.getCurrentTime());
                }
                if (getStatusResponse.hasDcOutputVoltageCurrent()) {
                    deviceStatus.setDcOutputVoltageCurrent(getStatusResponse.getDcOutputVoltageCurrent());
                }
                if (getStatusResponse.hasDcOutputVoltageMaximum()) {
                    deviceStatus.setDcOutputVoltageMaximum(getStatusResponse.getDcOutputVoltageMaximum());
                }
                if (getStatusResponse.hasEventNotificationMask()) {
                    deviceStatus.setEventNotificationsMask(getStatusResponse.getEventNotificationMask());
                }
                if (getStatusResponse.hasExternalFlashMemSize()) {
                    deviceStatus.setExternalFlashMemSize(getStatusResponse.getExternalFlashMemSize());
                }
                if (getStatusResponse.hasFirmwareVersion()) {
                    deviceStatus.setFirmwareVersion(getStatusResponse.getFirmwareVersion());
                }
                if (getStatusResponse.hasHardwareId()) {
                    deviceStatus.setHardwareId(getStatusResponse.getHardwareId());
                }
                if (getStatusResponse.hasInternalFlashMemSize()) {
                    deviceStatus.setInternalFlashMemSize(getStatusResponse.getInternalFlashMemSize());
                }
                if (getStatusResponse.hasLastInternalTestResultCode()) {
                    deviceStatus.setLastInternalTestResultCode(getStatusResponse.getLastInternalTestResultCode());
                }
                if (getStatusResponse.getMacAddress() != null && !getStatusResponse.getMacAddress().isEmpty()) {
                    deviceStatus.setMacAddress(this.convertMacAddress(getStatusResponse.getMacAddress()));
                }
                if (getStatusResponse.hasMaximumOutputPowerOnDcOutput()) {
                    deviceStatus.setMaximumOutputPowerOnDcOutput(getStatusResponse.getMaximumOutputPowerOnDcOutput());
                }
                if (getStatusResponse.hasName()) {
                    deviceStatus.setName(getStatusResponse.getName());
                }
                if (getStatusResponse.hasNumberOfOutputs()) {
                    deviceStatus.setNumberOfOutputs(getStatusResponse.getNumberOfOutputs());
                }
                if (getStatusResponse.hasSerialNumber()) {
                    deviceStatus.setSerialNumber(this.convertSerialNumber(getStatusResponse.getSerialNumber()));
                }
                if (getStatusResponse.hasStartupCounter()) {
                    deviceStatus.setStartupCounter(getStatusResponse.getStartupCounter());
                }
            } else {
                // handle failure by throwing exceptions if needed
                LOGGER.error("Unable to convert Oslp.GetStatusResponse");
            }
        }

        final DeviceResponse deviceResponse = new GetStatusDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), deviceStatus);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private String convertCurrentConfigurationBankUsed(final ByteString byteString) {
        return String.valueOf(byteString.toByteArray()[0]);
    }

    private String convertMacAddress(final ByteString byteString) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte b : byteString.toByteArray()) {
            stringBuilder.append(String.format("%02X", b)).append("-");
        }
        final String macAddress = stringBuilder.toString();
        LOGGER.info("macAddress: {}", macAddress);
        return macAddress.substring(0, macAddress.length() - 1);
    }

    private String convertSerialNumber(final ByteString byteString) {
        if (byteString == null) {
            return null;
        }
        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte b : byteString.toByteArray()) {
            stringBuilder.append(String.format("%02X", b));
        }
        return stringBuilder.toString();
    }

    private void handleOslpResponseResumeSchedule(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasResumeScheduleResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getResumeScheduleResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSetConfiguration(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetConfigurationResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetConfigurationResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSetEventNotifications(final DeviceRequest deviceRequest,
            final OslpEnvelope oslpResponse, final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetEventNotificationsResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetEventNotificationsResponse()
                    .getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSetLight(final DeviceRequest setLightdeviceRequest,
            final ResumeScheduleDeviceRequest resumeScheduleDeviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler setLightDeviceResponseHandler,
            final DeviceResponseHandler resumeScheduleDeviceResponseHandler) {
        this.saveOslpResponseLogEntry(setLightdeviceRequest, oslpResponse);

        this.updateSequenceNumber(setLightdeviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetLightResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetLightResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        // Send response to the message processor's device response handler.
        final DeviceResponse deviceResponse = new EmptyDeviceResponse(
                setLightdeviceRequest.getOrganisationIdentification(), setLightdeviceRequest.getDeviceIdentification(),
                setLightdeviceRequest.getCorrelationUid(), status);
        setLightDeviceResponseHandler.handleResponse(deviceResponse);

        if (this.executeResumeScheduleAfterSetLight && status.equals(DeviceMessageStatus.OK)) {
            LOGGER.info("Sending ResumeScheduleRequest for device: {}",
                    setLightdeviceRequest.getDeviceIdentification());
            this.resumeSchedule(resumeScheduleDeviceRequest);
        } else {
            LOGGER.info(
                    "Not sending ResumeScheduleRequest for device: {} because executeResumeScheduleAfterSetLight is false or DeviceMessageStatus is not OK",
                    setLightdeviceRequest.getDeviceIdentification());

            final DeviceResponse emptyDeviceResponse = new EmptyDeviceResponse(
                    setLightdeviceRequest.getOrganisationIdentification(),
                    setLightdeviceRequest.getDeviceIdentification(), setLightdeviceRequest.getCorrelationUid(), status);
            resumeScheduleDeviceResponseHandler.handleResponse(emptyDeviceResponse);
        }
    }

    private void handleOslpResponseSetReboot(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetRebootResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetRebootResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseSetTransition(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {

        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);
        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasSetTransitionResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getSetTransitionResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseStartSelfTest(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;
        if (oslpResponse.getPayloadMessage().hasStartSelfTestResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getStartSelfTestResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseStopSelfTest(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;
        if (oslpResponse.getPayloadMessage().hasStopSelfTestResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getStopSelfTestResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void handleOslpResponseUpdateFirmware(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse,
            final DeviceResponseHandler deviceResponseHandler) {
        this.saveOslpResponseLogEntry(deviceRequest, oslpResponse);

        this.updateSequenceNumber(deviceRequest.getDeviceIdentification(), oslpResponse);

        DeviceMessageStatus status;

        if (oslpResponse.getPayloadMessage().hasUpdateFirmwareResponse()) {
            final Oslp.Status oslpStatus = oslpResponse.getPayloadMessage().getUpdateFirmwareResponse().getStatus();
            status = this.mapper.map(oslpStatus, DeviceMessageStatus.class);
        } else {
            status = DeviceMessageStatus.FAILURE;
        }

        final DeviceResponse deviceResponse = new EmptyDeviceResponse(deviceRequest.getOrganisationIdentification(),
                deviceRequest.getDeviceIdentification(), deviceRequest.getCorrelationUid(), status);
        deviceResponseHandler.handleResponse(deviceResponse);
    }

    private void buildAndSignEnvelope(final DeviceRequest deviceRequest, final Oslp.Message payloadMessage,
            final Serializable extraData) {

        final String deviceIdentification = deviceRequest.getDeviceIdentification();
        final String organisationIdentification = deviceRequest.getOrganisationIdentification();
        final String correlationUid = deviceRequest.getCorrelationUid();
        final String ipAddress = deviceRequest.getIpAddress();
        final String domain = deviceRequest.getDomain();
        final String domainVersion = deviceRequest.getDomainVersion();
        final String messageType = deviceRequest.getMessageType();
        final int retryCount = deviceRequest.getRetryCount();
        final boolean isScheduled = deviceRequest.isScheduled();

        // Get some values from the database.
        final OslpDevice oslpDevice = this.oslpDeviceSettingsService
                .getDeviceByDeviceIdentification(deviceIdentification);
        final byte[] deviceId = Base64.decodeBase64(oslpDevice.getDeviceUid());
        final byte[] sequenceNumber = SequenceNumberUtils.convertIntegerToByteArray(oslpDevice.getSequenceNumber());

        this.oslpSigningService.buildAndSignEnvelope(organisationIdentification, deviceIdentification, correlationUid,
                deviceId, sequenceNumber, ipAddress, domain, domainVersion, messageType, retryCount, isScheduled,
                payloadMessage, extraData);
    }

    private Oslp.LightValue buildLightValue(final LightValueDto lightValue) {
        final Oslp.LightValue.Builder builder = Oslp.LightValue.newBuilder();

        if (lightValue.getIndex() != null) {
            builder.setIndex(ByteString.copyFrom(new byte[] { lightValue.getIndex().byteValue() }));
        }

        builder.setOn(lightValue.isOn());

        if (lightValue.getDimValue() != null) {
            builder.setDimValue(ByteString.copyFrom(new byte[] { lightValue.getDimValue().byteValue() }));
        }

        return builder.build();
    }

    /**
     * Return the correct port, depending on loopback or external.
     */
    private InetSocketAddress createAddress(final InetAddress address) {
        if (address.isLoopbackAddress()) {
            return new InetSocketAddress(address, this.oslpPortClientLocal);
        }

        return new InetSocketAddress(address, this.oslpPortClient);
    }

    private InetSocketAddress createAddress(final String ipAddress) throws UnknownHostException {
        final InetAddress inetAddress = InetAddress.getByName(ipAddress);

        return this.createAddress(inetAddress);
    }

    private void saveOslpResponseLogEntry(final DeviceRequest deviceRequest, final OslpEnvelope oslpResponse) {
        final OslpDevice oslpDevice = this.oslpDeviceSettingsService
                .getDeviceByDeviceIdentification(deviceRequest.getDeviceIdentification());

        final OslpLogItemRequestMessage oslpLogItemRequestMessage = new OslpLogItemRequestMessage(
                deviceRequest.getOrganisationIdentification(), oslpDevice.getDeviceUid(),
                deviceRequest.getDeviceIdentification(), true, oslpResponse.isValid(), oslpResponse.getPayloadMessage(),
                oslpResponse.getSize());

        this.oslpLogItemRequestMessageSender.send(oslpLogItemRequestMessage);
    }

    private void saveOslpRequestLogEntry(final DeviceRequest deviceRequest, final OslpEnvelope oslpRequest) {
        final OslpDevice oslpDevice = this.oslpDeviceSettingsService
                .getDeviceByDeviceIdentification(deviceRequest.getDeviceIdentification());

        final OslpLogItemRequestMessage oslpLogItemRequestMessage = new OslpLogItemRequestMessage(
                deviceRequest.getOrganisationIdentification(), oslpDevice.getDeviceUid(),
                deviceRequest.getDeviceIdentification(), false, true, oslpRequest.getPayloadMessage(),
                oslpRequest.getSize());

        this.oslpLogItemRequestMessageSender.send(oslpLogItemRequestMessage);
    }

    private void updateSequenceNumber(final String deviceIdentification, final OslpEnvelope oslpResponse) {
        final Integer sequenceNumber = SequenceNumberUtils.convertByteArrayToInteger(oslpResponse.getSequenceNumber());

        final OslpDevice oslpDevice = this.oslpDeviceSettingsService
                .getDeviceByDeviceIdentification(deviceIdentification);
        oslpDevice.setSequenceNumber(sequenceNumber);
        this.oslpDeviceSettingsService.updateDeviceAndForceSave(oslpDevice);
    }

    private void sendMessage(final String ipAddress, final OslpEnvelope oslpRequest,
            final OslpResponseHandler oslpResponseHandler, final DeviceRequest deviceRequest) throws IOException {
        try {
            this.oslpChannelHandler.send(this.createAddress(ipAddress), oslpRequest, oslpResponseHandler,
                    deviceRequest.getDeviceIdentification());
        } catch (final Exception e) {
            LOGGER.error("Exception during sendMessage()", e);
            throw new IOException(e.getMessage());
        }
    }

    // === PROTECTED SETTERS FOR TESTING ===

    public void setOslpPortClient(final int oslpPortClient) {
        this.oslpPortClient = oslpPortClient;
    }

    public void setOslpPortClientLocal(final int oslpPortClientLocal) {
        this.oslpPortClientLocal = oslpPortClientLocal;
    }

    public void setMapper(final OslpMapper mapper) {
        this.mapper = mapper;
    }

    public void setOslpChannelHandler(final OslpChannelHandlerClient channelHandler) {
        this.oslpChannelHandler = channelHandler;
    }

}
