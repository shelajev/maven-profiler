package org.shelajev.maven.profiler;

import java.io.File;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.maven.eventspy.AbstractEventSpy;

import org.apache.maven.eventspy.EventSpy;
import org.codehaus.plexus.component.annotations.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

@Component(role = EventSpy.class)
public class BuildProfiler extends AbstractEventSpy {
  public static final String PATHNAME = "data.txt";

  private final Timer scheduler = new Timer();
  private final com.sun.management.OperatingSystemMXBean bean;
  private PrintWriter out;

  public BuildProfiler() {
    this.bean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    try {
      out = new PrintWriter(new File(PATHNAME));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void init(Context context)
    throws Exception {
    super.init(context);
    double[] lastCPU = new double[1];
    scheduler.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        double cpuLoad = bean.getCpuLoad();

        if (Double.isNaN(cpuLoad)) {
          cpuLoad = lastCPU[0];
        } else {
          lastCPU[0] = cpuLoad;
        }
        double usedMem = 1.0 - (bean.getFreeMemorySize() * 1.0 / bean.getTotalMemorySize());

        long ms = System.currentTimeMillis();
        out.println("%s %f %f".formatted(ms, cpuLoad, usedMem));
      }
    }, 10, 300);
  }

  @Override
  public void onEvent(Object event) throws Exception {

  }


    @Override
  public void close() {
    scheduler.cancel();
    out.flush();
    out.close();

    GenericContainer<?> plotille = new GenericContainer<>(
      new ImageFromDockerfile("maven-profiler-plotille").withDockerfile(Paths.get(MountableFile.forClasspathResource("Dockerfile").getResolvedPath())))
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("graph.py"),
        "/graph.py")
      .withCopyFileToContainer(MountableFile.forHostPath(PATHNAME),
        "/data.txt")
      .withStartupCheckStrategy(new OneShotStartupCheckStrategy());

    plotille.start();
    String logs = plotille.getLogs();

    System.out.println(logs);
  }

}
