package de.disk0.logging.test.app;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
@EnableAutoConfiguration(exclude={org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class})
@ComponentScan(basePackages={"de.disk0"})
public class LoggingTestApp {
	
	private static Log log = LogFactory.getLog(LoggingTestApp.class);
	
	public static void main(String[] args) {
	    SpringApplication.run(LoggingTestApp.class, args);
	}

	@Scheduled(fixedDelay=5000)
	public void printLog() {
		log.info("Log output: "+new SimpleDateFormat().format(new Date()));
		log.error("Log output: "+new SimpleDateFormat().format(new Date()), new RuntimeException());
	}
	
	
}
