package ee.neotech.util;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class MeasureValve extends ValveBase {

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        try (StopWatch sw = StopWatch.start("Request-Valve", 200)) {
            getNext().invoke(request, response);
        }        
    }

}
