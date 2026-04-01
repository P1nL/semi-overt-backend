package com.platform.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.platform.web.support.config.PlatformWebSupportConfig;

@SpringBootApplication(scanBasePackageClasses = FileServiceApplication.class, exclude = {DataSourceAutoConfiguration.class})
@Import(PlatformWebSupportConfig.class)
/**
 * 鏂囦欢鏈嶅姟鍚姩绫伙紝璐熻矗鍚姩鏂囦欢妯″潡搴旂敤涓婁笅鏂囥€?
 */

public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}

