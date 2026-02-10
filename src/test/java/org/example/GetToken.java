package org.example;

import java.io.IOException;

public class GetToken {

    public static void main(String[] args) throws IOException {
        ToolsController toolsController= new ToolsController();
        String token  = toolsController.getAPISEGToken();

        System.out.println(token);

    }
}
