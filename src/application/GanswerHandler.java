package application;
import java.io.IOException;  

import javax.servlet.ServletException;  
import javax.servlet.http.HttpServletRequest;  
import javax.servlet.http.HttpServletResponse;  

import log.QueryLogger;

import org.json.*;
import org.eclipse.jetty.server.Request;  
import org.eclipse.jetty.server.handler.AbstractHandler;

import rdf.Sparql;
import qa.GAnswer;
import qa.Globals;
import qa.Matches;

public class GanswerHandler extends AbstractHandler{
	public static String errorHandle(String status,String message,String question,QueryLogger qlog){
		JSONObject exobj = new JSONObject();
		try {
			exobj.put("status", status);
			exobj.put("message", message);
			exobj.put("questions", question);
			if(qlog!=null&&qlog.rankedSparqls!=null&&qlog.rankedSparqls.size()>0){
				exobj.put("sparql", qlog.rankedSparqls.get(0).toStringForGStore2());
			}
		} catch (Exception e1) {
		}
		return exobj.toString();
	}
	
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)  
            throws IOException, ServletException {
		String question = "";
		QueryLogger qlog = null;
		try{
			response.setContentType("text/html;charset=utf-8");  
	        response.setStatus(HttpServletResponse.SC_OK);
	        //step1: parsing input json
	        String data = request.getParameter("data");
	        data = data.replace("%22","\"");
	        JSONObject jsonobj = new JSONObject();
	        int needAnswer = 0;
	        int needSparql = 1;
	        question = "Show me all Czech movies";
			jsonobj = new JSONObject(data);
			needAnswer = jsonobj.getInt("maxAnswerNum");
			needSparql = jsonobj.getInt("needSparql");
			question = jsonobj.getString("questions");
			Globals.MaxAnswerNum = needAnswer;
	        
	        //step2 run GAnswer Logic
	        String input = question;
	        GAnswer ga = new GAnswer();
	        qlog = ga.getSparqlList(input);
	        if(qlog == null || qlog.rankedSparqls == null){
				try {
					baseRequest.setHandled(true);
					response.getWriter().println(errorHandle("500","UnvalidQuestionException: the question you input is invalid, please check",question,qlog));
				} catch (Exception e1) {
				}
	        	return;
	        }
	        int idx;
			
			//step2 construct response
			JSONObject resobj = new JSONObject();
			resobj.put("status", "200");
			resobj.put("id","test");
			resobj.put("query",jsonobj.getString("questions"));
			JSONObject tmpobj = new JSONObject();
			if(needAnswer > 0){
				if(qlog!=null && qlog.rankedSparqls.size()!=0){
					Sparql curSpq = null;
					Matches m = null;
					for(idx = 1;idx<=Math.min(qlog.rankedSparqls.size(), 5);idx+=1){
						curSpq = qlog.rankedSparqls.get(idx-1);
						if(curSpq.tripleList.size()>0&&curSpq.questionFocus!=null){
							m = ga.getAnswerFromGStore2(curSpq);
						}
						if(m!=null&&m.answers!=null){
							qlog.sparql = curSpq;
							qlog.match = m;
							break;
						}
					}
					curSpq = ga.getUntypedSparql(curSpq);
					if(curSpq!=null){
						m = ga.getAnswerFromGStore2(curSpq);
					}
					if(m!=null&&m.answers!=null){
						qlog.sparql = curSpq;
						qlog.match = m;
					}
					if(qlog.match==null)
						qlog.match=new Matches();
					if(qlog.sparql==null)
						qlog.sparql = qlog.rankedSparqls.get(0);
					qlog.reviseAnswers();
					
					//adding variables to result json
					JSONArray vararr = new JSONArray();
					for(String var : qlog.sparql.variables){
						vararr.put(var);
					}
					resobj.put("vars", vararr);
					
					//adding answers to result json
					JSONArray ansobj = new JSONArray();
					JSONObject bindingobj;
					System.out.println(qlog.match.answersNum);
					for(int i=0;i<qlog.match.answersNum;i++){
						int j = 0;
						bindingobj = new JSONObject();
						for(String var:qlog.sparql.variables){
							JSONObject bidobj = new JSONObject();
							if(qlog.match.answers[i][j].startsWith("\""))
								bidobj.put("type", "literal");
							else
								bidobj.put("type", "uri");
							String[] ansRiv = qlog.match.answers[i][j].split(":");
							bidobj.put("value", ansRiv[ansRiv.length-1]);
							System.out.println(qlog.match.answers[i][j]);
							j += 1;
							bindingobj.put(var, bidobj);
						}
						ansobj.put(bindingobj);
					}
					tmpobj.put("bindings", ansobj);
				}
				resobj.put("results", tmpobj);
			}
			if(needSparql>0){
				JSONArray spqarr = new JSONArray();
				for(idx=0;idx<needSparql&&idx<qlog.rankedSparqls.size();idx+=1){
					spqarr.put(qlog.rankedSparqls.get(idx).toStringForGStore2());
				}
				resobj.put("sparql", spqarr);
			} 
	        baseRequest.setHandled(true);  
	        response.getWriter().println(resobj.toString());
		}
		catch(Exception e){
			if(e instanceof IOException){
				try {
					baseRequest.setHandled(true);
					response.getWriter().println(errorHandle("500","IOException",question,qlog));
				} catch (Exception e1) {
				}
			}
			else if(e instanceof JSONException){
				try {
					baseRequest.setHandled(true);
					response.getWriter().println(errorHandle("500","JSONException",question,qlog));
				} catch (Exception e1) {
				}
			}
			else if(e instanceof ServletException){
				try {
					baseRequest.setHandled(true);
					response.getWriter().println(errorHandle("500","ServletException",question,qlog));
				} catch (Exception e1) {
				}
			}
			else {
				try {
					baseRequest.setHandled(true);
					response.getWriter().println(errorHandle("500","Unkown Exception",question,qlog));
				} catch (Exception e1) {
				}
			}
		} 
    }  
}
