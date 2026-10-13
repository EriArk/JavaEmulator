/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import org.junit.After;
import org.junit.Before;

/** Reuse the scene contracts, including exact sample checks, with hardware preparation. */
public class GpuHardwareSceneContractTest extends GpuSceneContractTest {
    @Before @Override public void setup() { super.setup(); GpuGeometry.enabled=true; }
    @After @Override public void release() { super.release(); GpuGeometry.enabled=false; }
}
