package ca.bc.gov.nrs.taps;

import ca.bc.gov.nrs.taps.configuration.OracleTlsCompatibility;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TapsApplication {
  public static void main(String[] args) {
    OracleTlsCompatibility.allowRsaKeyExchange();
    SpringApplication.run(TapsApplication.class, args);
  }
}
