import java.net.Socket;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.util.Scanner;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedWriter;

public class client{
/*
Linkkejä joista katsoin esimerkkejä
http://www.careerbless.com/samplecodes/java/beginners/socket/SocketBasic1.php
http://edn.embarcadero.com/article/31995
*/
public static void main(String[] args)
{
try {
	Scanner keys = new Scanner(System.in);
// 	System.out.println("Enter IP address:");
//	String addr = keys.nextLine(); // kysyy IP osoitetta

	// Luo socketin
	String addr = "10.20.202.242";
	Socket socket = new Socket(addr, 8080); // Luo socketin
	System.out.println("Connection Established");
	BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	boolean i = true;
	while (i)
	{
		try { 
		OutputStream out = socket.getOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(out);
		BufferedWriter BW = new BufferedWriter(osw);

		// lähetettävä viesti
		Scanner koos = new Scanner(System.in);
		System.out.println("Enter the message to be sent to the server:");
		String msg = koos.nextLine();

		
		if (msg.equals("e")){ // sulkee yhteyden jos käyttäjä syöttää "e"-merkin
			i = false;
			BW.write(msg);
			BW.flush();
			System.out.println("Closing connection");
			System.exit(0);
		} else {
			msg = msg + "\n";
			BW.write(msg);
			BW.flush();
			System.out.println("\nText Sent: " + msg);

			InputStream ins = socket.getInputStream();
			InputStreamReader in_reader = new InputStreamReader(ins);
			BufferedReader BR = new BufferedReader(in_reader);

		// mikä viesti saatiin vastaukseksi
			StringBuilder sb = new StringBuilder();	
			String msg_got; 
//			System.out.println("Message received");
			/*for (msg_got = BR.readLine();msg_got != null; msg_got = BR.readLine())
			{
			//	System.out.println("a");
				sb.append(msg_got);
				System.out.print(msg_got);	
			}
			BR.close();
			*/
			msg_got = BR.readLine();
			System.out.println("Message received: "+ msg_got);
		}

		} catch (IOException e) {
		System.out.println("No I/O");
		System.exit(1);	
		}	
	}
	socket.close();
	} catch (UnknownHostException e) {
	System.out.println("Unknown host");
	System.exit(1);
	
	} catch (IOException e) {
	System.out.println("No I/O");
	System.exit(1);
	}

}
}
