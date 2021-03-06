package com.example.flowabledemo;

import com.example.flowabledemo.service.MyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Liujs
 */
@SpringBootApplication
@EnableSwagger2
public class FlowableDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowableDemoApplication.class, args);
    }

    // @Bean
    // public CommandLineRunner init(final RepositoryService repositoryService,
    //                               final RuntimeService runtimeService,
    //                               final TaskService taskService) {
    //
    //     return new CommandLineRunner() {
    //         @Override
    //         public void run(String... strings) throws Exception {
    //             System.out.println("Number of process definitions : "
    //                     + repositoryService.createProcessDefinitionQuery().count());
    //             System.out.println("Number of tasks : " + taskService.createTaskQuery().count());
    //             runtimeService.startProcessInstanceByKey("oneTaskProcess");
    //             System.out.println("Number of tasks after process start: "
    //                     + taskService.createTaskQuery().count());
    //         }
    //     };
    // }


    /**
     * 建几个用户
     *
     * @param myService
     * @return
     */
    @Bean
    public CommandLineRunner init(final MyService myService) {

        return new CommandLineRunner() {
            @Override
            public void run(String... strings) throws Exception {
                myService.createDemoUsers();
            }
        };
    }

}
