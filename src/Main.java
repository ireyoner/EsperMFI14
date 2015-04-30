import java.io.IOException;

import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;

public class Main {
    public static void main(String[] args) throws IOException {
        EPServiceProvider serviceProvider = EPServiceProviderManager.getDefaultProvider();
        EPAdministrator administrator = serviceProvider.getEPAdministrator();

        administrator.createEPL(""
        		+ "create window for14days.std:groupwin(spolka).win:length(14)"
        		+ "  (data Date"
        		+ "  ,spolka String"
        		+ "  ,obrotForsyPlus Double"
        		+ "  ,obrotForsyMinus Double"
        		+ "  ,kursDywergencji Double"
//        		+ "  ,kursSredni Double"
        		+ "  )");
        administrator.createEPL(""
        		+ "insert into for14days "
        		+ "select data as data"
        		+ "     , spolka as spolka"
        		+ "     , case when (wartoscMax + wartoscMin + kursZamkniecia) >= (prev(1, wartoscMax) + prev(1, wartoscMin) + prev(1, kursZamkniecia))"
        		+ "            then obrot * (wartoscMax + wartoscMin + kursZamkniecia) / 3 else 0d end as obrotForsyPlus"
        		+ "     , case when (wartoscMax + wartoscMin + kursZamkniecia) < (prev(1, wartoscMax) + prev(1, wartoscMin) + prev(1, kursZamkniecia))"
        		+ "            then obrot * (wartoscMax + wartoscMin + kursZamkniecia) / 3 else 0d end as obrotForsyMinus"
        		+ "     , (wartoscMax + wartoscMin + kursZamkniecia) / 3 as kursDywergencji "
//        		+ "     , (wartoscMax + wartoscMin + kursZamkniecia) / 3 as kursSredni"
        		+ "  from KursAkcji.std:groupwin(spolka).win:length(2)");

        administrator.createEPL(""
        		+ "create window for3days.std:groupwin(spolka).win:length(3)"
        		+ "  (data Date"
        		+ "  ,spolka String"
        		+ "  ,MFI_14 Double"
        		+ "  ,kursDywergencji Double"
        		+ "  )");
        administrator.createEPL(""
        		+ "insert into for3days"
        		+ "select data as data"
        		+ "     , spolka as spolka"
        		+ "     , 100d - ( 100d / ( 1d "
        		+ "                 + ((sum(obrotForsyPlus))  "
              + "                   /  (sum(obrotForsyMinus)) )"
              + "                   )) as MFI_14"
              + "       , kursDywergencji as kursDywergencji  "
        		+ "  from for14days group by spolka");
        EPStatement statement = administrator.createEPL(""
        		+ "select *  "
        		+ "  from for3days ");

      ProstyListener listener = new ProstyListener();
      statement.addListener(listener);

        InputStream inputStream = new InputStream();
        inputStream.generuj(serviceProvider);
    }

}