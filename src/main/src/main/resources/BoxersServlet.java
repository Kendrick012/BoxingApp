package com.boxingapp;

import java.io*; //handle input and output
import java.servlet.*; //class to act like Servlet
import javax.servlet.http.*; //respond to HTTP requests
import java.sql.*; //connect to MySQL
import org.json.JSONArray;
import org.json.JSONObject;

public class BoxersServlet extends HttpServlet{ //Inherit to reposnd to HTTP requests
    protected void doGet(HttoServletRequest request, HttpServletResponse response)//to run automatically when the browser calls GET
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();

    try{
        String idParam = request.getParameter("id");
        if(idParam == null){
            out.print("{\"error\":\"No ID provided\"}");
            return;
        }

    Class.forName("com.mysql.cj.jdbc.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:myswl://localhost:3306/boxingpedia_db", "root", "");

    PreparedStatement pstmt = conn.prepareStatement("Select * FROM boxers WHERE id", "");
    stmt.setInt(1, id);
    ResultSet rs = stnt.excuteQuery();


    if (rs.next()){
        JSONObject boxer = new JSONObject();
        boxer.put("image_url", rs.getString("image_url"));
        boxer.put("name", rs.getString("name"));
        boxer.put("nationality", rs.getString("nationality"));
        boxer.put("record", rs.getString("record"));
        boxer.put("stance", rs.getString("stance"));
        boxer.put("age", rs.getString("age"));
        boxer.put("height", rs.getString("height"));
        boxer.put("reach", rs.getString("reach"));

        out.print(boxersArray.toString());
    } else{
        out.print("{\"error\":\"Boxer not found\"}");
    }

    conn.close();
}
catch (Exception e){
    e.printStackTrace();
    out.print("{\"error\":\"Server error\"}");
        }
    }
}
