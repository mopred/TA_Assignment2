package com.santi.jaime.TA;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletApp extends HttpServlet{

	
	private static final long	serialVersionUID	= 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		PrintWriter pw = resp.getWriter();
		resp.setContentType("text/html");
		pw.println("<html>");
		pw.println("<head>");
		pw.println("<title>TA Assignment 2 Servlet Service</title>");
		pw.println("</head><body bgcolor=\"yellow\">");
		pw.println("<h1>Welcome to our Assignment 2 Servlet!</h1><br /><br />");
		pw.println("<h3>Please select one the following options</h3>");
		
		pw.println("</body></html>");
		
		
	}
}
