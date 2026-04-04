package com.scai.customer_portal;

import com.scai.customer_portal.config.JiraProperties;
import com.scai.customer_portal.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ JwtProperties.class, JiraProperties.class })
public class CustomerPortalApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerPortalApplication.class, args);
	}

}
