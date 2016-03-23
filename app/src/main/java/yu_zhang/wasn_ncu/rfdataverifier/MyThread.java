package yu_zhang.wasn_ncu.rfdataverifier;

/**
 * Created by yu-zhang on 3/10/16.
 */
public class MyThread extends Thread
{
    String s;
    Object syncToken;
    public MyThread(Object syncToken)
    {
        this.s = "";
        this.syncToken = syncToken;
    }

    public void run()
    {
        while(true) // you will need to set some condition if you want to stop the thread in a certain time...
        {
            synchronized (syncToken)
            {
                try
                {
                    syncToken.wait();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            System.out.println("MyThread: " + s);
        }
    }

    public void setText(String s)
    {
        this.s = s;
    }
}