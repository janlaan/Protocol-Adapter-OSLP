/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.elster.application.mapping;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.MutableDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alliander.osgp.dto.valueobjects.ConfigurationDto;
import com.alliander.osgp.dto.valueobjects.DaliConfigurationDto;
import com.alliander.osgp.dto.valueobjects.DeviceFixedIpDto;
import com.alliander.osgp.dto.valueobjects.LightTypeDto;
import com.alliander.osgp.dto.valueobjects.LinkTypeDto;
import com.alliander.osgp.dto.valueobjects.LongTermIntervalTypeDto;
import com.alliander.osgp.dto.valueobjects.MeterTypeDto;
import com.alliander.osgp.dto.valueobjects.RelayConfigurationDto;
import com.alliander.osgp.dto.valueobjects.RelayMapDto;
import com.alliander.osgp.dto.valueobjects.RelayMatrixDto;
import com.alliander.osgp.oslp.Oslp;
import com.google.protobuf.ByteString;

import ma.glasnost.orika.CustomConverter;
import ma.glasnost.orika.MappingContext;
import ma.glasnost.orika.metadata.Type;

public class OslpGetConfigurationResponseToConfigurationConverter
        extends CustomConverter<Oslp.GetConfigurationResponse, ConfigurationDto> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(OslpGetConfigurationResponseToConfigurationConverter.class);

    @Override
    public ConfigurationDto convert(final Oslp.GetConfigurationResponse source,
            final Type<? extends ConfigurationDto> destinationType, final MappingContext context) {

        // @formatter:off
        // Convert the required values for the constructor of Configuration.
        final LightTypeDto lightType = source.hasLightType()
                ? this.mapperFacade.map(source.getLightType(), LightTypeDto.class)
                : null;
        final DaliConfigurationDto daliConfiguration = source.hasDaliConfiguration()
                ? this.mapperFacade.map(source.getDaliConfiguration(), DaliConfigurationDto.class)
                : null;
        final Integer shortTermHistoryIntervalMinutes = source.hasShortTermHistoryIntervalMinutes()
                ? this.mapperFacade.map(source.getShortTermHistoryIntervalMinutes(), Integer.class)
                : null;
        final RelayConfigurationDto relayConfiguration = source.hasRelayConfiguration()
                ? this.mapperFacade.map(source.getRelayConfiguration(), RelayConfigurationDto.class)
                : new RelayConfigurationDto(new ArrayList<RelayMapDto>());
        final LinkTypeDto preferredLinkType = source.hasPreferredLinkType()
                && !source.getPreferredLinkType().equals(Oslp.LinkType.LINK_NOT_SET)
                        ? this.mapperFacade.map(source.getPreferredLinkType(), LinkTypeDto.class)
                        : null;
        final MeterTypeDto meterType = source.hasMeterType() && !source.getMeterType().equals(Oslp.MeterType.MT_NOT_SET)
                ? this.mapperFacade.map(source.getMeterType(), MeterTypeDto.class)
                : null;
        final Integer longTermHistoryInterval = source.hasLongTermHistoryInterval()
                ? this.mapperFacade.map(source.getLongTermHistoryInterval(), Integer.class)
                : null;
        final LongTermIntervalTypeDto longTermHistoryIntervalType = source.hasLongTermHistoryIntervalType()
                && !source.getLongTermHistoryIntervalType().equals(Oslp.LongTermIntervalType.LT_INT_NOT_SET)
                        ? this.mapperFacade.map(source.getLongTermHistoryIntervalType(), LongTermIntervalTypeDto.class)
                        : null;

        final ConfigurationDto configuration = ConfigurationDto.newBuilder().withLightType(lightType)
                .withDaliConfiguration(daliConfiguration).withRelayConfiguration(relayConfiguration)
                .withShortTermHistoryIntervalMinutes(shortTermHistoryIntervalMinutes)
                .withPreferredLinkType(preferredLinkType).withMeterType(meterType)
                .withLongTermHistoryInterval(longTermHistoryInterval)
                .withLongTermHysteryIntervalType(longTermHistoryIntervalType).build();

        // Set the optional values using the set() functions.
        configuration.setTimeSyncFrequency(source.getTimeSyncFrequency());
        if (source.getDeviceFixIpValue() != null && !source.getDeviceFixIpValue().isEmpty()) {
            final String ipAddress = this.convertIpAddress(source.getDeviceFixIpValue());
            final String netMask = this.convertIpAddress(source.getNetMask());
            final String gateWay = this.convertIpAddress(source.getGateWay());
            configuration.setDeviceFixedIp(new DeviceFixedIpDto(ipAddress, netMask, gateWay));
        }
        configuration.setDhcpEnabled(source.getIsDhcpEnabled());
        configuration.setCommunicationTimeout(source.getCommunicationTimeout());
        configuration.setCommunicationNumberOfRetries(source.getCommunicationNumberOfRetries());
        configuration.setCommunicationPauseTimeBetweenConnectionTrials(
                source.getCommunicationPauseTimeBetweenConnectionTrials());
        if (source.getOspgIpAddress() != null && !source.getOspgIpAddress().isEmpty()) {
            configuration.setOsgpIpAddress(this.convertIpAddress(source.getOspgIpAddress()));
        }
        if (source.getOsgpPortNumber() > 0 && source.getOsgpPortNumber() < 65536) {
            configuration.setOsgpPortNumber(source.getOsgpPortNumber());
        }
        configuration.setTestButtonEnabled(source.getIsTestButtonEnabled());
        configuration.setAutomaticSummerTimingEnabled(source.getIsAutomaticSummerTimingEnabled());
        configuration.setAstroGateSunRiseOffset(source.getAstroGateSunRiseOffset());
        configuration.setAstroGateSunSetOffset(source.getAstroGateSunSetOffset());
        configuration.setSwitchingDelays(source.getSwitchingDelayList());
        if (source.getRelayLinkingList() != null) {
            configuration
                    .setRelayLinking(this.mapperFacade.mapAsList(source.getRelayLinkingList(), RelayMatrixDto.class));
        }
        configuration.setRelayRefreshing(source.getRelayRefreshing());

        final DateTime summerTimeDetails = this.convertSummerTimeWinterTimeDetails(source.getSummerTimeDetails());
        configuration.setSummerTimeDetails(summerTimeDetails);
        final DateTime winterTimeDetails = this.convertSummerTimeWinterTimeDetails(source.getWinterTimeDetails());
        configuration.setWinterTimeDetails(winterTimeDetails);

        return configuration;
        // @formatter:on
    }

    private String convertIpAddress(final ByteString byteString) {
        if (byteString == null || byteString.isEmpty()) {
            return "";
        }
        LOGGER.debug("byteString.toByteArray().length(): {}", byteString.toByteArray().length);

        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte number : byteString.toByteArray()) {
            int convertedNumber = number;
            if (number < 0) {
                convertedNumber = 256 + number;
            }
            final String str = String.valueOf(convertedNumber);
            stringBuilder.append(str).append(".");
        }
        final String ipValue = stringBuilder.toString();
        return ipValue.substring(0, ipValue.length() - 1);
    }

    // @formatter:off
    /*
     * SummerTimeDetails/WinterTimeDetails string: MMWHHmi
     *
     * where: (note, north hemisphere summer begins at the end of march) MM:
     * month W: day of the week (0- Monday, 6- Sunday) HH: hour of the changing
     * time mi: minutes of the changing time
     *
     * Default value for summer time: 0360100 Default value for summer time:
     * 1060200
     */
    // @formatter:on
    private DateTime convertSummerTimeWinterTimeDetails(final String timeDetails) {
        final int month = Integer.parseInt(timeDetails.substring(0, 2));
        final int day = Integer.parseInt(timeDetails.substring(2, 3));
        final int hour = Integer.parseInt(timeDetails.substring(3, 5));
        final int minutes = Integer.parseInt(timeDetails.substring(5, 7));

        LOGGER.info("month: {}, day: {}, hour: {}, minutes: {}", month, day, hour, minutes);

        final int year = DateTime.now().getYear();
        final int dayOfMonth = this.getLastDayOfMonth(month, day);
        final DateTime dateTime = new DateTime(year, month, dayOfMonth, hour, minutes);

        LOGGER.info("dateTime: {}", dateTime.toString());

        return dateTime;
    }

    /**
     * For a given Month of this year, find the date for the weekday {@link day}
     * .
     */
    private int getLastDayOfMonth(final int month, final int day) {
        final DateTime dateTime = DateTime.now();
        MutableDateTime x = dateTime.toMutableDateTime();
        x.set(DateTimeFieldType.monthOfYear(), month);
        x.set(DateTimeFieldType.dayOfMonth(), 31);

        x = this.findLastDayOfOfMonth(day, x);
        return x.getDayOfMonth();
    }

    /**
     * Loop backwards through the days of the month until we find {@link day} of
     * the month. For example the last Sunday of the month March of this year.
     */
    private MutableDateTime findLastDayOfOfMonth(final int day, final MutableDateTime x) {
        final int yodaTimeDay = day + 1;
        while (true) {
            if (yodaTimeDay == x.getDayOfWeek()) {
                break;
            } else {
                final int dayOfMonth = x.getDayOfMonth() - 1;
                x.set(DateTimeFieldType.dayOfMonth(), dayOfMonth);
            }
        }
        return x;
    }
}
