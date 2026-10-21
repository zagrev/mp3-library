package com.example.mp3library;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class Mp3LibraryApplicationTest
{

  @Test
  void mainDelegatesToSpringApplicationRun()
  {
    try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class))
    {
      Mp3LibraryApplication.main(new String[]
      { "--spring.main.web-application-type=none" });

      springApplication.verify(() -> SpringApplication.run(eq(Mp3LibraryApplication.class), any(String[].class)));
    }
  }
}
