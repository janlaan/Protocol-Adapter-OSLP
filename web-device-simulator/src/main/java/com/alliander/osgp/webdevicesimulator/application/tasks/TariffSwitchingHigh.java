package com.alliander.osgp.webdevicesimulator.application.tasks;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alliander.osgp.oslp.Oslp;
import com.alliander.osgp.webdevicesimulator.application.services.DeviceManagementService;
import com.alliander.osgp.webdevicesimulator.domain.entities.Device;
import com.alliander.osgp.webdevicesimulator.domain.repositories.DeviceRepository;
import com.alliander.osgp.webdevicesimulator.service.RegisterDevice;
import com.alliander.osgp.webdevicesimulator.service.SwitchingServices;

@Component
public class TariffSwitchingHigh implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffSwitchingHigh.class);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RegisterDevice registerDevice;

    @Autowired
    private SwitchingServices switchingServices;

    @Autowired
    private DeviceManagementService deviceManagementService;

    @Override
    public void run() {

        if (this.deviceManagementService.getTariffSwitching()) {
            LOGGER.info("TariffSwitching on for devices");

            final List<Device> devices = this.deviceRepository.findAll();

            for (final Device device : devices) {
                LOGGER.info("Tariff switching on for : {}: {} ", device.getId(), device.getDeviceIdentification());

                // Switching on Tariff
                this.switchingServices.tariffSwitchHigh(device.getId());

                // Send EventNotifications for TariffSwitching Off
                LOGGER.info("Sending TARIFF_EVENTS_TARIFF_ON event for device : {}: {} ", device.getId(),
                        device.getDeviceIdentification());
                this.registerDevice.sendEventNotificationCommand(device.getId(),
                        Oslp.Event.TARIFF_EVENTS_TARIFF_ON_VALUE,
                        "TARIFF_EVENTS_TARIFF_ON event occurred on Tariff Switching high ", 1);
            }
        }
    }
}