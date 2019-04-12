package org.nd4j.jita.allocator.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import lombok.val;
import org.nd4j.jita.allocator.pointers.CudaPointer;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.nativeblas.NativeOpsHolder;

public class MemoryTracker {

    private List<AtomicLong> allocatedPerDevice = new ArrayList<>();
    private List<AtomicLong> cachedPerDevice = new ArrayList<>();
    private List<AtomicLong> totalPerDevice = new ArrayList<>();
    private List<AtomicLong> workspacesPerDevice = new ArrayList<>();
    private final static MemoryTracker INSTANCE = new MemoryTracker();

    public MemoryTracker() {
        for (int i = 0; i < Nd4j.getAffinityManager().getNumberOfDevices(); ++i) {
            allocatedPerDevice.add(i, new AtomicLong(0));
            cachedPerDevice.add(i, new AtomicLong(0));
	        workspacesPerDevice.add(i, new AtomicLong(0));

            val memory = NativeOpsHolder.getInstance().getDeviceNativeOps().getDeviceTotalMemory(new CudaPointer(i));
            totalPerDevice.add(i, new AtomicLong(memory));
        }
    }

    public static MemoryTracker getInstance() {
        return INSTANCE;
    }

    public long getAllocatedAmount(int deviceId) {
        return allocatedPerDevice.get(deviceId).get();
    }

    public long getCachedAmount(int deviceId) {
        return cachedPerDevice.get(deviceId).get();
    }

    public long getWorkspaceAllocatedAmount(int deviceId) {
        return workspacesPerDevice.get(deviceId).get();
    }

    public long getTotalMemory(int deviceId) {
        return totalPerDevice.get(deviceId).get();
    }

    /**
     * This method returns total amount of device memory allocated on specified device
     *
     * Includes: workspace memory, cached memory, regular memory
     * @param deviceId
     * @return
     */
    public long getActiveMemory(int deviceId) {
        return getWorkspaceAllocatedAmount(deviceId) +  getAllocatedAmount(deviceId) + getCachedAmount(deviceId);
    }

    /**
     * This method returns amount of memory that relies on JVM GC
     *
     * Includes: cached memory, regular allocated memory
     *
     * @param deviceId
     * @return
     */
    public long getManagedMemory(int deviceId) {
        return getAllocatedAmount(deviceId) + getCachedAmount(deviceId);
    }

    /**
     * This method increments amount of regular allocated memory
     *
     * @param deviceId
     * @param memoryAdded
     */
    public void incrementAllocatedAmount(int deviceId, long memoryAdded) {
        allocatedPerDevice.get(deviceId).getAndAdd(memoryAdded);
    }

    /**
     * This method increments amount of cached memory
     *
     * @param deviceId
     * @param memoryAdded
     */
    public void incrementCachedAmount(int deviceId, long memoryAdded) {
        cachedPerDevice.get(deviceId).getAndAdd(memoryAdded);
    }

    /**
     * This method decrements amount of regular allocated memory
     *
     * @param deviceId
     * @param memoryAdded
     */
    public void decrementAllocatedAmount(int deviceId, long memoryAdded) {
        allocatedPerDevice.get(deviceId).getAndAdd(-memoryAdded);
    }

    /**
     * This method decrements amount of cached memory
     *
     * @param deviceId
     * @param memorySubtracted
     */
    public void decrementCachedAmount(int deviceId, long memorySubtracted) {
        cachedPerDevice.get(deviceId).getAndAdd(-memorySubtracted);
    }

    /**
     * This method increments amount of memory allocated within workspaces
     *
     * @param deviceId
     * @param memoryAdded
     */
    public void incrementWorkspaceAllocatedAmount(int deviceId, long memoryAdded) {
        workspacesPerDevice.get(deviceId).getAndAdd(memoryAdded);
    }

    /**
     * This method decrements amount of memory allocated within workspaces
     *
     * @param deviceId
     * @param memorySubtracted
     */
    public void decrementWorkspaceAmount(int deviceId, long memorySubtracted) {
        workspacesPerDevice.get(deviceId).getAndAdd(-memorySubtracted);
    }


    private void setTotalPerDevice(int device, long memoryAvailable) {
        totalPerDevice.add(device, new AtomicLong(memoryAvailable));
    }
}