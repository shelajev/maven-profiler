package org.shelajev.maven.profiler;

import java.io.File;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.model.Info;
import org.apache.maven.eventspy.AbstractEventSpy;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.codehaus.plexus.component.annotations.Component;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import org.apache.maven.execution.ExecutionEvent.Type;

import static org.apache.maven.execution.ExecutionEvent.Type.MojoStarted;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class BuildProfiler extends AbstractEventSpy {
  public static final String PATHNAME = "data.txt";
  public static final String PHASES_PATHNAME = "phases.txt";

  private final Timer scheduler = new Timer();
  private final com.sun.management.OperatingSystemMXBean bean;
  private PrintWriter dataFile;
  private PrintWriter lifecycleFile;

  @Inject
  public BuildProfiler() {
    this.bean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    try {
      dataFile = new PrintWriter(new File(PATHNAME));
      lifecycleFile = new PrintWriter(new File(PHASES_PATHNAME));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void init(Context context)
    throws Exception {
    super.init(context);

    DockerClient dockerClient = DockerClientFactory.lazyClient();
    InfoCmd infoCmd = dockerClient.infoCmd();
    Info exec = infoCmd.exec();
    System.out.println(exec.toString());


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
        dataFile.println("%s %f %f".formatted(ms, cpuLoad, usedMem));
      }
    }, 10, 300);
  }


  private Set<String> phases = new HashSet<>();

  @Override
  public void onEvent(Object event) throws Exception {
    System.out.println(event.toString());
    if(event instanceof ExecutionEvent) {
      ExecutionEvent executionEvent = (ExecutionEvent) event;
      Type type = executionEvent.getType();
      if(MojoStarted == type) {
        String phase = executionEvent.getMojoExecution().getLifecyclePhase();
        if(phase != null) {
          long ms = System.currentTimeMillis();
          lifecycleFile.println("%s %s".formatted(ms, phase));
        }
      }
    }
  }


    @Override
  public void close() {
    scheduler.cancel();
    dataFile.flush();
    dataFile.close();
    lifecycleFile.flush();
    lifecycleFile.close();

    GenericContainer<?> plotille = new GenericContainer<>(
      new ImageFromDockerfile("maven-profiler-plotille").withDockerfile(Paths.get(MountableFile.forClasspathResource("Dockerfile").getResolvedPath())))
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("graph.py"),
        "/graph.py")
      .withCopyFileToContainer(MountableFile.forHostPath(PATHNAME),
        "/data.txt")
      .withCopyFileToContainer(MountableFile.forHostPath(PHASES_PATHNAME),
        "/phases.txt")
      .withStartupCheckStrategy(new OneShotStartupCheckStrategy());

    plotille.start();
    String logs = plotille.getLogs();

    System.out.println(logs);
  }

}
