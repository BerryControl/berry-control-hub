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
package com.github.berrycontrol.drivermanager;

import com.github.berrycontrol.driver.api.BerryHubDeviceDriver;
import com.github.berrycontrol.driver.api.BerryHubDeviceDriverDescriptor;
import com.github.berrycontrol.driver.api.BerryHubDeviceDriverException;
import com.github.berrycontrol.driver.api.BerryHubDeviceInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

@Component
public class DriverManager {
    private final static Logger logger = LoggerFactory.getLogger(DriverManager.class);

    private List<Path> driverPaths = new ArrayList<>();

    private List<BerryHubDeviceDriverDescriptor> drivers = new ArrayList<>();

    public DriverManager(Path ...pluginPaths) {
        if (pluginPaths != null) {
            this.driverPaths.addAll(Arrays.asList(pluginPaths));
        }
    }

    public List<BerryHubDeviceDriverDescriptor> getDrivers() {
        if (drivers.isEmpty()) {
            this.loadDrivers();
        }

        return drivers;
    }

    public Optional<BerryHubDeviceDriverDescriptor> getDriver(UUID driverId) {
        return this.getDrivers()
            .stream()
            .filter(drv -> drv.getDriverId().equals(driverId))
            .findFirst();
    }

    public List<BerryHubDeviceInfo> getDeviceInfos(BerryHubDeviceDriverDescriptor driver) throws BerryHubDeviceDriverException {
        return driver.getDevices();
    }

    public Optional<BerryHubDeviceInfo> getDeviceInfo(BerryHubDeviceDriverDescriptor driver, String deviceId) {
        logger.info("Loading device info from driver >{}< for deviceid = {}", driver.getDisplayName(), deviceId);

        try {
            return this
                .getDeviceInfos(driver)
                .stream()
                .filter(dev -> dev.getDeviceId().equals(deviceId))
                .findFirst();
        } catch (BerryHubDeviceDriverException e) {
            logger.error("Error while getting info for device >{}< from driver >{}<",
                deviceId, driver.getDisplayName(), e);
            return Optional.empty();
        }
    }

    public void loadDrivers() {
        List<Path> jarPaths = getJarPaths(this.driverPaths);

        drivers = loadDriverJars(jarPaths);
    }

    private List<BerryHubDeviceDriverDescriptor> loadDriverJars(List<Path> jarPaths) {
        List<BerryHubDeviceDriverDescriptor> drvs = new ArrayList<>();

        jarPaths.forEach(path -> {
            try {
                Manifest manifest = loadManifest(path);

                loadDriver(manifest, path).ifPresent(drvs::add);
            } catch (Exception ex) {
                logger.error("Error while loading driver from >{}<", path, ex);
            }
        });

        return drvs;
    }

    private Optional<BerryHubDeviceDriverDescriptor> loadDriver(Manifest manifest, Path path) {
        Map<Object, Object> attributes = manifest.getMainAttributes();
        String driverClass = (String) attributes.get(new Attributes.Name("Driver-Class"));
        String driverId = (String) attributes.get(new Attributes.Name("Driver-Id"));
        String driverProvider = (String) attributes.get(new Attributes.Name("Driver-Provider"));
        String driverVersion = (String) attributes.get(new Attributes.Name("Driver-Version"));

        logger.info("Loading driver: Driver-Class = {}, Driver-ID = {}, Driver-Provider = {}, Driver-Version = {}",
            driverClass, driverId, driverProvider, driverVersion);

        if (!StringUtils.isEmpty(driverClass)) {
            try {
                URLClassLoader child = new URLClassLoader(
                    new URL[] { path.toUri().toURL() },
                    this.getClass().getClassLoader()
                );
                Class<?> classToLoad = Class.forName(driverClass, true, child);
                Constructor<?> constructor =  classToLoad.getConstructor(null);
                BerryHubDeviceDriverDescriptor drv = (BerryHubDeviceDriverDescriptor) constructor.newInstance();
                return Optional.ofNullable(drv);
            } catch (MalformedURLException e) {
                // TODO
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        logger.info("Driver-Class is not specified, not loading driver!");
        return Optional.empty();
    }

    private Manifest loadManifest(Path path) {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(path.toFile()))) {
            return jis.getManifest();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> getJarPaths(List<Path> pluginPaths) {
        List<Path> jarPaths = new ArrayList<>();

        for (Path pluginPath: pluginPaths) {
            jarPaths.addAll(getJarPathsFrom(pluginPath));
        }

        return jarPaths;
    }

    private Collection<? extends Path> getJarPathsFrom(Path pluginPath) {
        List<Path> jarPaths = new ArrayList<>();
        File dir = pluginPath.toFile();

        logger.debug("Searching plugin JAR files in >{}<.", pluginPath.toAbsolutePath());

        if (dir.exists() && dir.isDirectory() && dir.canRead()) {
            jarPaths
                .addAll(
                    Arrays.stream(dir.listFiles((d, name) -> name.endsWith(".jar")))
                        .filter(f -> f.exists() && f.isFile() && f.canRead())
                        .map(f -> Path.of(f.getAbsolutePath()))
                        .toList());
        }

        logger.debug("Found {} plugin JAR files.", jarPaths.size());

        return jarPaths;
    }

    public Optional<BerryHubDeviceDriver> getDriverInstance(String driverId, String deviceId) {
        Optional<BerryHubDeviceDriverDescriptor> driver = this.getDriver(UUID.fromString(driverId));

        return driver.map(descriptor -> descriptor.createDriverInstance(deviceId));
    }
}
