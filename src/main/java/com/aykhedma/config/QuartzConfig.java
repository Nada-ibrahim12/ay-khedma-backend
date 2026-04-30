package com.aykhedma.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Configuration
public class QuartzConfig
{
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(ApplicationContext applicationContext)
    {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);

        factory.setJobFactory(jobFactory);
        return factory;
    }
}
