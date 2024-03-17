/*
 *    Copyright 2024 Thomas Bonk <thomas@meandmymac.de>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.github.berrycontrol.api;

import com.github.berrycontrol.driver.api.BerryHubDeviceDriverDescriptor;
import com.github.berrycontrol.driver.api.BerryHubDeviceDriverException;
import com.github.berrycontrol.driver.api.BerryHubDeviceInfo;
import com.github.berrycontrol.driver.api.StartPairingResult;
import com.github.berrycontrol.drivermanager.DriverManager;
import com.github.berrycontrol.persistence.model.PairedDevice;
import com.github.berrycontrol.persistence.repository.PairedDevicesRepository;
import com.github.berrycontrol.server.api.DeviceDriversApiDelegate;
import com.github.berrycontrol.server.model.DeviceDriver;
import com.github.berrycontrol.server.model.DeviceInfo;
import com.github.berrycontrol.server.model.FinalizePairingRequest;
import com.github.berrycontrol.server.model.FinalizePairingResponse;
import com.github.berrycontrol.server.model.StartPairingRequest;
import com.github.berrycontrol.server.model.StartPairingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceDriversDelegate extends AbstractApiDelegate implements DeviceDriversApiDelegate {
    private final static Logger logger = LoggerFactory.getLogger(DeviceDriversDelegate.class);

    private final DriverManager driverManager;
    private final  PairedDevicesRepository pairedDevicesRepository;

    public DeviceDriversDelegate(DriverManager driverManager, PairedDevicesRepository pairedDevicesRepository) {
        this.driverManager = driverManager;
        this.pairedDevicesRepository = pairedDevicesRepository;
    }

    @Override
    public ResponseEntity<List<DeviceDriver>> readDeviceDrivers() {
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                return ResponseEntity.ok(
                    this.driverManager.getDrivers().stream().map(this::toDeviceDriver).toList());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).<List<DeviceDriver>>build();
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @Override
    public ResponseEntity<List<DeviceInfo>> readDevices(UUID driverId) {
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                return this.driverManager
                    .getDriver(driverId)
                    .map(drv -> ResponseEntity.ok(getDevicesList(drv)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).<List<DeviceInfo>>build();
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @Override
    public ResponseEntity<StartPairingResponse> startPairing(
        UUID driverId, String deviceId, StartPairingRequest startPairingRequest) {

        if(this.getRequest().isPresent()) {
            NativeWebRequest request = this.getRequest().get();

            if (acceptsApplicationJson(request)) {
                Optional<BerryHubDeviceDriverDescriptor> driverOptional = this.driverManager.getDriver(driverId);

                if (driverOptional.isPresent()) {
                    BerryHubDeviceDriverDescriptor drv = driverOptional.get();
                    Optional<BerryHubDeviceInfo> deviceInfoOptional = this.driverManager.getDeviceInfo(drv, deviceId);

                    if (deviceInfoOptional.isPresent()) {
                        BerryHubDeviceInfo deviceInfo = deviceInfoOptional.get();
                        try {
                            StartPairingResult pairingResult = drv.startPairing(deviceInfo, startPairingRequest.getRemoteName());
                            return ResponseEntity.ok(new StartPairingResponse()
                                .pairingRequest(UUID.fromString(pairingResult.getPairingRequestId()))
                                .deviceProvidesPin(pairingResult.deviceProvidesPin()));
                        } catch (BerryHubDeviceDriverException e) {
                            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error while retrieving device info.");
                        }
                    }
                }

                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver or device not found.");
            }

            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Client doesn't accept application/json response.");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client sent an invalid request.");
    }

    @Override
    public ResponseEntity<FinalizePairingResponse> finalizePairing(
        UUID driverId, String deviceId, UUID pairingRequestId, FinalizePairingRequest finalizePairingRequest) {

        if(this.getRequest().isPresent()) {
            NativeWebRequest request = this.getRequest().get();

            if (acceptsApplicationJson(request)) {
                Optional<BerryHubDeviceDriverDescriptor> driverOptional = this.driverManager.getDriver(driverId);

                if (driverOptional.isPresent()) {
                    BerryHubDeviceDriverDescriptor drv = driverOptional.get();

                    try {
                        boolean paired = drv.finalizePairing(
                            pairingRequestId.toString(),
                            finalizePairingRequest.getPin(),
                            finalizePairingRequest.getDeviceProvidesPin());

                        if (paired) {
                            // persist paired device
                            pairedDevicesRepository
                                .save(
                                    PairedDevice
                                        .builder()
                                        .id(UUID.randomUUID())
                                        .driverId(driverId.toString())
                                        .deviceId(deviceId)
                                        .deviceName(
                                            String
                                                .format(
                                                    "%s (%s)",
                                                    this.driverManager.getDeviceInfo(drv, deviceId).get().getName(),
                                                    drv.getDisplayName()))
                                        .build());
                        }

                        return ResponseEntity.ok(new FinalizePairingResponse().deviceHasPaired(paired));
                    } catch (BerryHubDeviceDriverException e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error while finalizing pairing request.");
                    }
                }

                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver or device not found.");
            }

            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Client doesn't accept application/json response.");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client sent an invalid request.");
    }

    private List<DeviceInfo> getDevicesList(BerryHubDeviceDriverDescriptor driver) {
        return toDeviceInfos(driver);
    }

    private List<DeviceInfo> toDeviceInfos(BerryHubDeviceDriverDescriptor driver) {

        try {
            //noinspection unchecked
            return this.driverManager
                .getDeviceInfos(driver)
                .stream()
                .map(devInf -> this.toDeviceInfo((BerryHubDeviceInfo) devInf))
                .toList();
        } catch (BerryHubDeviceDriverException e) {
            logger.error("Error while retrieving devices from driver {} ({}).", driver.getDisplayName(), driver.getDriverId(), e);
            return List.of();
        }
    }

    private DeviceInfo toDeviceInfo(BerryHubDeviceInfo devInf) {
        return new DeviceInfo()
            .deviceId(devInf.getDeviceId())
            .name(devInf.getName());
    }

    private DeviceDriver toDeviceDriver(BerryHubDeviceDriverDescriptor descr) {
        DeviceDriver drv = new DeviceDriver();

        drv.setDriverId(descr.getDriverId());
        drv.setDisplayName(descr.getDisplayName());
        drv.setDescription(descr.getDescription());
        drv.setAuthenticationMethod(
            DeviceDriver.AuthenticationMethodEnum.fromValue(descr.authenticationMethod().name()));

        return drv;
    }
}
