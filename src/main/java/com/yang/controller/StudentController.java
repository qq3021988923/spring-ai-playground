package com.yang.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/stu")
public class StudentController {

    // http://localhost:8090/stu/demos
    @GetMapping("/demos")
    public String upload(){


        return "计算机应用技术";
    }

}
