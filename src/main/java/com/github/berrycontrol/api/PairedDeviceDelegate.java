package com.github.berrycontrol.api;

import com.github.berrycontrol.driver.api.BerryHubDeviceCommand;
import com.github.berrycontrol.driver.api.BerryHubDeviceDriver;
import com.github.berrycontrol.drivermanager.DriverManager;
import com.github.berrycontrol.persistence.repository.PairedDevicesRepository;
import com.github.berrycontrol.server.api.PairedDevicesApiDelegate;
import com.github.berrycontrol.server.model.DeviceCommand;
import com.github.berrycontrol.server.model.PairedDevice;
import com.github.berrycontrol.server.model.RemoteLayout;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

@Service
public class PairedDeviceDelegate extends AbstractApiDelegate implements PairedDevicesApiDelegate {
    private final static Logger logger = LoggerFactory.getLogger(PairedDeviceDelegate.class);

    private final DriverManager driverManager;
    private final PairedDevicesRepository pairedDevicesRepository;

    public PairedDeviceDelegate(DriverManager driverManager, PairedDevicesRepository pairedDevicesRepository) {
        this.driverManager = driverManager;
        this.pairedDevicesRepository = pairedDevicesRepository;
    }

    @Override
    public ResponseEntity<List<PairedDevice>> readPairedDevices() {
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                return ResponseEntity.ok(
                    this.pairedDevicesRepository.findAll().stream().map(this::toPairedDevice).toList());
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            }
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    @Override
    public ResponseEntity<Void> unpairDevice(UUID pairingId) {
        return this.getRequest().map(request -> {
            this.pairedDevicesRepository
                .findById(pairingId)
                .ifPresentOrElse(
                    this.pairedDevicesRepository::delete,
                    () -> {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                    });

            return ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build();
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    private PairedDevice toPairedDevice(com.github.berrycontrol.persistence.model.PairedDevice pairedDevice) {
        return new PairedDevice()
            .pairingId(pairedDevice.getId())
            .driverId(UUID.fromString(pairedDevice.getDriverId()))
            .deviceId(pairedDevice.getDeviceId())
            .deviceName(pairedDevice.getDeviceName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public ResponseEntity<List<DeviceCommand>> readDeviceCommands(UUID pairingId) {
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                Optional<com.github.berrycontrol.persistence.model.PairedDevice> pairedDevice =
                    this.pairedDevicesRepository.findById(pairingId);

                if (pairedDevice.isPresent()) {
                    Optional<BerryHubDeviceDriver> device =
                        this.driverManager.getDriverInstance(
                            pairedDevice.get().getDriverId(), pairedDevice.get().getDeviceId());

                    if (device.isPresent()) {
                        return ResponseEntity.ok((List<DeviceCommand>) device.get().getCommands().stream()
                            .map(cmd ->
                                new DeviceCommand()
                                    .pairingId(pairingId)
                                    .driverId(UUID.fromString(pairedDevice.get().getDriverId()))
                                    .deviceId(pairedDevice.get().getDeviceId())
                                    .commandId(((BerryHubDeviceCommand) cmd).getId())
                                    .name(((BerryHubDeviceCommand) cmd).getTitle())
                                    .icon(((BerryHubDeviceCommand) cmd).getIcon()))
                            .toList());
                    }
                }

                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            }
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ResponseEntity<RemoteLayout> readDeviceRemoteLayout(UUID pairingId) {
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                Optional<com.github.berrycontrol.persistence.model.PairedDevice> pairedDevice =
                    this.pairedDevicesRepository.findById(pairingId);

                if (pairedDevice.isPresent()) {
                    Optional<BerryHubDeviceDriver> device =
                        this.driverManager.getDriverInstance(
                            pairedDevice.get().getDriverId(), pairedDevice.get().getDeviceId());

                    if (device.isPresent()) {
                        return ResponseEntity.ok(
                            new RemoteLayout()
                                .width(device.get().getRemoteLayoutWidth())
                                .height(device.get().getRemoteLayoutHeight())
                                .buttons(this.toButtonsList(device.get().getRemoteLayout())));
                    }
                }

                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            }
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    private List<List<Integer>> toButtonsList(int[][] remoteLayout) {
        return Arrays.stream(remoteLayout)
            .map(row -> IntStream.of(row)
                .boxed()
                .toList())
            .toList();
    }

    @Override
    public ResponseEntity<Void> executeDeviceCommand(UUID pairingId, Integer commandId) {
        //return PairedDevicesApiDelegate.super.executeDeviceCommand(pairingId, commandId);
        return this.getRequest().map(request -> {
            if (acceptsApplicationJson(request)) {
                Optional<com.github.berrycontrol.persistence.model.PairedDevice> pairedDevice =
                    this.pairedDevicesRepository.findById(pairingId);

                if (pairedDevice.isPresent()) {
                    Optional<BerryHubDeviceDriver> device =
                        this.driverManager.getDriverInstance(
                            pairedDevice.get().getDriverId(), pairedDevice.get().getDeviceId());

                    device
                        .get()
                        .getCommand(commandId)
                        .ifPresentOrElse(
                            cmd -> device.get().execute((BerryHubDeviceCommand) cmd),
                            () -> { throw new ResponseStatusException(HttpStatus.NOT_FOUND); });

                    return new ResponseEntity(HttpStatus.NO_CONTENT);
                }

                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            }
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }
}
