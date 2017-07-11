/*
 * DeviceFiscalPrinterHasar.java
 *
 * Created on 25/11/2008, 01:08:55
 */
package com.openbravo.pos.printer.tfhka;

import com.openbravo.pos.forms.*;
import com.openbravo.pos.printer.DeviceFiscalPrinter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Calendar;
//import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.openbravo.data.loader.Session;
import org.jdesktop.layout.*;
import javax.swing.JComponent;
//import javax.swing.LayoutStyle;

import com.openbravo.basic.BasicException;
import com.openbravo.beans.JCalendarDialog;
import com.openbravo.data.gui.*;
import com.openbravo.format.Formats;

import tfhka.*;
import tfhka.pa.*;

/**
 *
 * @author Marcos
 */
public class DeviceFiscalPrinterTfhka extends javax.swing.JPanel implements DeviceFiscalPrinter {

    private String Port = "";
    private Tfhka tf = null;
    private Machine MyMaq = null;
    private boolean isDevolucion = false;
    private String MnsError = "";
    private boolean indSincrono = false;
    private final String my_indexCach;
    private double totalAcumuladoSystem = 0.0;
    private int indexPLU_Reg = 0;
    private PrinterStatus StatusErrorPrinter;
    private S2PrinterData S2Estado;

    /**
     * Creates new form DeviceFiscalPrinterHasar
     */
    public DeviceFiscalPrinterTfhka(String portserial, Session s, String indexCaja) {
        this.Port = portserial;
        boolean pin;
        try {
            this.tf = new Tfhka(this.Port);
            //	PrintWriter out2 = new  PrintWriter( "/home/"+ System.getProperty("user.name")+"/Corrida.txt" );  //Linux
            PrintWriter out2 = new PrintWriter("C:\\Users\\" + System.getProperty("user.name") + "\\Corrida.txt");  //Windows
        } catch (IOException ioe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error al crear el anchivo Log: Corrida.txt. " + ioe.getMessage()));
            System.exit(0);
        }
        this.my_indexCach = indexCaja;
        //Chequeo de Presencia de Impreosora Fiscal 
        pin = this.tf.CheckFprinter();
        if (!(this.tf.IndPuerto && pin)) {
            if (!this.tf.IndPuerto) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error al Abrir el Puerto Serial: " + Port + "  configurado para la Impresora Fiscal. Verifique su disponibilidad. Designe en configuración otro puerto que se encuentre disponible."));
                //System.exit(0);
            } else if (!pin) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detectó la Impresora Fiscal. Verifique su conexión"));
                //System.exit(0);
            }

        } else {
    	 // Sincronización del Sistema con la Impresora Fiscal

            this.SincronizarMaquina(s);
            /*Para Panama*/
            //  this.MyMaq  = new Machine(tf,s);
            //  this.indSincrono =true;

            // Inicio los Paneles gráficos
            initComponents();
        }

    }

    // Metodo de Sincronizacion

    private void SincronizarMaquina(Session s) {
        this.MyMaq = new Machine(tf, s);
        int a = 0;

        while (MyMaq.getSerial() == null && a < 5) {
            log("Intentando por " + a);
            this.MyMaq = new Machine(tf, s);
            ++a;

        }

        if (MyMaq.getSerial() != null) {
            log("Consultando Serial = " + MyMaq.getSerial());

            String nroMaq = MyMaq.ConsultarSerial(MyMaq.getSerial());

            if (nroMaq != null) {
                boolean isTaxes = MyMaq.CompararTasas();

                if (isTaxes) {
                    boolean isContadores = MyMaq.CompararContadores(MyMaq.getSerial());

                    if (isContadores) {
                        try {
                            MyMaq.AjustarFechaHora();
                            log("TODO OK......");
                            this.indSincrono = true;
                        } catch (PrinterException pex) {
                            this.indSincrono = false;
                            this.MnsError = pex.getMessage();
                            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Hubo Problema al Ajustar la Fecha-Hora de la Impresora Fiscal. Verifique su conexión y/o Memoria." + this.MnsError));

                            //log(this.MnsError);
                        }
                    } else {
                        if (this.MyMaq.isZ_X_cero() && this.MyMaq.isCajaSystem_Zero(this.my_indexCach)) {
                            this.indSincrono = true;
                        } else {
                            this.indSincrono = false;
                            this.MnsError = MyMaq.msnError + " El Administrador debe hacer un Cierre de caja en el Sistema y un Repote Z Directo.";
                            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));
                        }
                        //log(this.MnsError);
                    }
                } else {
                    this.indSincrono = false;
                    this.MnsError = "Las tasas No Coinciden. Por Favor Programelas como estan en el sistema. " + MyMaq.msnError;
                    JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));
                    //log(this.MnsError);
                }
            } else {

                this.MnsError = "No se encuentra el serial.";
                //Ver historial y cargar Serial actual
                String serialUltimo = MyMaq.DameUltimoSerial();

                if (serialUltimo != null) {
                    if (this.MyMaq.isZ_X_cero() && this.MyMaq.isCajaSystem_Zero(this.my_indexCach)) {
                        int y = MyMaq.CargarSerial();

                        log("Carga de serial = " + y);

                        if (y == 1) {
                            boolean sontax = MyMaq.CompararTasas();

                            if (sontax) {
                                this.indSincrono = true;
                                log("Tasas cargadas...");
                                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_SUCCESS, "Se ha Detectado un Cambio de Equipo."));
                            } else {
                                this.indSincrono = false;
                                this.MnsError = "Las tasas No Coinciden. Por Favor Programelas como estan en el sistema.";
                                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));
                                //log(this.MnsError);
                            }

                        } else if (y == -1) {
                            this.indSincrono = false;
                            this.MnsError = "Error al Cargar Serial. " + this.MyMaq.msnError;
                            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));

                        } else if (y == 0) {
                            this.indSincrono = false;
                            this.MnsError = "No se pudo Cargar el Serial. " + this.MyMaq.msnError;
                            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));

                        }

                    } else {
                        this.indSincrono = false;
                        this.MnsError = "Se ha detectado un cambio de Equipo en el sistema. El Administrador debe Hacer un Cierre de Caja con equipo Anterior.";
                        JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));
                    }
                } else {

                    int y = MyMaq.CargarSerial();

                    log("Carga de serial = " + y);

                    boolean sontax = MyMaq.CompararTasas();

                    if (y == 1) {
                        if (sontax) {
                            this.indSincrono = true;
                            log("Tasas cargadas...");
                        } else {
                            this.indSincrono = false;
                            this.MnsError = "Las tasas No Coinciden. Por Favor Programelas como estan en el sistema.";
                            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));
                            //log(this.MnsError);
                        }
                    } else if (y == -1) {
                        this.indSincrono = false;
                        this.MnsError = "Error al Cargar Serial. " + this.MyMaq.msnError;
                        JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));

                    } else if (y == 0) {
                        this.indSincrono = false;
                        this.MnsError = "No se pudo Cargar el Serial. " + this.MyMaq.msnError;
                        JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, this.MnsError));

                    }

                }

            }
        } else {
            // log("Hubo Problema al Subir la data de la Impresora");
            this.indSincrono = false;
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Hubo Problema al Subir la Data de la Impresora Fiscal. Verifique su conexión y/o Memoria."));
            System.exit(0);
        }

    }

    public boolean isSincrono() {
        return this.indSincrono;
    }

    private void log(String text) {
        try {
            //  PrintWriter out = new PrintWriter( new FileOutputStream("/home/pablo/main/Corrida.txt", true) );
            PrintWriter out = new PrintWriter(new FileOutputStream("Corrida.txt", true));
            out.println(System.currentTimeMillis() + " -> " + text);
            out.close();
        } catch (Exception ex) {
            Logger.getLogger(DeviceFiscalPrinterTfhka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String getFiscalName() {
        // log("getFiscalName");
        return "Impresora Fiscal";
    }

    public JComponent getFiscalComponent() {
        // log("getFiscalComponent");
        return this;
    }

    public Object getDriver() {
        return this.tf;
    }

    public void beginReceipt() {
        //log("beginReceipt");
    }

    public void endReceipt() {
        //log("endReceipt");
    }

    public void printLine(String sproduct, double dprice, double dunits) throws PrinterException {

        String comandoLinea = "";

        String precio = String.valueOf(dprice);
        String[] Precio_def = partir_valor(precio);

        // Obtengo la trama entera del porcentaje
        while (Precio_def[0].length() < 2) {
            Precio_def[0] = "0" + Precio_def[0];
        }
        // Obtengo la trama decimal del porcentaje
        if (Precio_def[1].length() > 2) {
            Precio_def[1] = Precio_def[1].substring(0, 2);
        } else {
            while (Precio_def[1].length() < 2) {
                Precio_def[1] = Precio_def[1] + "0";
            }
        }

        if (sproduct.equals("p-")) {  // Es un descuento por porcentaje
            comandoLinea = "p-" + Precio_def[0] + Precio_def[1];
        } else if (sproduct.equals("p+")) { // Es un Recargo por porcentaje
            comandoLinea = "p+" + Precio_def[0] + Precio_def[1];
        } else if (sproduct.substring(0, 1).equals("q")) { // Ajuste por monto
            // Obtengo la trama entera del monto
            while (Precio_def[0].length() < 7) {
                Precio_def[0] = "0" + Precio_def[0];
            }
            // Obtengo la trama decimal del monto
            if (Precio_def[1].length() > 2) {
                Precio_def[1] = Precio_def[1].substring(0, 2);
            } else {
                while (Precio_def[1].length() < 2) {
                    Precio_def[1] = Precio_def[1] + "0";
                }
            }

            if (sproduct.equals("q-")) {// Es un descuento por monto
                comandoLinea = "q-" + Precio_def[0] + Precio_def[1];
            } else { // Es un recargo por monto
                comandoLinea = "q+" + Precio_def[0] + Precio_def[1];
            }
        }

        try {
            boolean trans = this.tf.SendCmd(comandoLinea);
            if (!trans) {

                this.tf.getS2PrinterData();

                String[] montoSystem = this.partir_valor(String.valueOf(totalAcumuladoSystem));

                while (montoSystem[1].length() < 2) {
                    montoSystem[1] = montoSystem[1] + "0";
                }

                String acumuladoTotalSystem = montoSystem[0] + "." + montoSystem[1].substring(0, 2);
                log("Monto en Sistema = " + acumuladoTotalSystem + " :  Monto en Impresora Fiscal = " + S2Estado.getAmountToPay());

                //Verifico Status y Error
                StatusErrorPrinter = this.tf.getPrinterStatus();

                log(" Status = " + StatusErrorPrinter.getPrinterStatusCode() + " Error = " + StatusErrorPrinter.getPrinterErrorCode());

                if (S2Estado.getAmountToPay()!= Double.valueOf(acumuladoTotalSystem)) {

                    log("No se registro la ultima linea");

                    boolean isAnula = this.tf.SendCmd("7");
                    if (isAnula) {
                        this.MyMaq.ModificarContadores();
                    }
                    totalAcumuladoSystem = 0.0;

                    throw new PrinterException("Error en Transacción al registrar item.", StatusErrorPrinter);
                }
            }
        } catch (PrinterException pe) {
            log("No se registro la útima linea.  " + pe.getMessage());

            boolean isAnula = this.tf.SendCmd("7");
            if (isAnula) {
                this.MyMaq.ModificarContadores();
            }
            totalAcumuladoSystem = 0.0;

            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            throw pe;
        } catch (java.lang.RuntimeException re) {
            log("No se registro la útima linea.  " + re.getMessage());

            boolean isAnula = this.tf.SendCmd("7");
            if (isAnula) {
                this.MyMaq.ModificarContadores();
            }
            totalAcumuladoSystem = 0.0;

            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            throw re;
        }
    }

    public void printMessage(String smessage) {
        try {
            this.tf.SendCmd(smessage);
        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        }
    }

    @Override
    public void printTotal(String sPayment, double dpaid) {

        try {
            S2Estado = this.tf.getS2PrinterData();
            double montoIF = S2Estado.getAmountToPay();
            try {
                Thread.sleep(5);
            } catch (java.lang.InterruptedException exp) {
            }
            if (montoIF > dpaid) {
                log("Monto Sistemico  : " + dpaid);
                log("Monto fiscal  : " + montoIF);

                double diferencia = montoIF - dpaid;
                log("Hay una Diferencia de : " + diferencia);

                if (diferencia < 0.02) {
                    dpaid = diferencia + dpaid;
                    log("Nuevo Monto Sistemico  : " + dpaid);
                }

            }

            log(String.format("printTotal: %s : %f\n", sPayment, dpaid));
            log("PAGANDO CON = " + sPayment);

            String precio = FormatearNormal(dpaid);
            precio = precio.replace(".", "");
            precio = precio.replace(",", ".");

            String[] Precio_def = partir_valor(precio);

            // Obtengo la trama entera del precio
            while (Precio_def[0].length() < 10) {
                Precio_def[0] = "0" + Precio_def[0];
            }
            // Obtengo la trama decimal del precio
            if (Precio_def[1].length() > 2) {
                Precio_def[1] = Precio_def[1].substring(0, 2);
            } else {
                while (Precio_def[1].length() < 2) {
                    Precio_def[1] = Precio_def[1] + "0";
                }
            }

            String medio = "01";

            if (sPayment.equals("Paper"))//Ticket Cesta
            {
                medio = "02";
            } else if (sPayment.equals("Mag card"))//Tarjeta Credito
            {
                medio = "13";
            } else if (sPayment.equals("Cheque"))//Cheque
            {
                medio = "05";
            } else if (sPayment.equals("Cash"))//Efectivo
            {
                medio = "01";
            }

            String cierre = "";

            if (!isDevolucion) {
                cierre = "2" + medio + Precio_def[0] + Precio_def[1];
            } else {
                cierre = "f" + medio + Precio_def[0] + Precio_def[1];
                this.isDevolucion = false;
            }

            log("Cierre = " + cierre);

            boolean istra = this.tf.SendCmd(cierre);

            if (istra) {
                this.MyMaq.ModificarContadores();
            } else {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se pudo Cerrar la factura. " + this.tf.Estado));
                boolean isAnula = this.tf.SendCmd("7");
                if (isAnula) {
                    this.MyMaq.ModificarContadores();
                }
                throw new PrinterException("Error al cerrar la Factura.", StatusErrorPrinter);
            }
            totalAcumuladoSystem = 0.0;
            this.indexPLU_Reg = 0;
        } catch (PrinterException pe) {
            log(pe.getMessage());

            try {
                boolean isAnula = this.tf.SendCmd("7");
                if (isAnula) {
                    this.MyMaq.ModificarContadores();
                }
            } catch (PrinterException pe2) {
            }
            totalAcumuladoSystem = 0.0;
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));

        } catch (java.lang.RuntimeException re) {
            log(re.getMessage());
            try {
                boolean isAnula = this.tf.SendCmd("7");
                if (isAnula) {
                    this.MyMaq.ModificarContadores();
                }
            } catch (PrinterException pe2) {
            }
            totalAcumuladoSystem = 0.0;
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            throw re;
        }
    }

    @Override
    public void printZReport() {

        try {
            boolean rep = this.tf.SendCmd("I0Z");
            return;
        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            return;
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            return;
        }

    }

    @Override
    public void printXReport() {

        try {
            this.tf.printXReport();
        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        }
    }

    private String[] partir_valor(String valor) {
        int x = 0;
        String[] fracion = new String[2];
        fracion[0] = "";
        fracion[1] = "";

        char[] caracteres = null;
        caracteres = valor.toCharArray();
        while (x < valor.length()) {
            if (caracteres[x] != '.') {
                fracion[0] += caracteres[x];
                ++x;
            } else {
                fracion[1] = valor.substring(x + 1);
                x = valor.length();
            }

        }

        return fracion;

    }

    private void btnDateStartActionPerformed(java.awt.event.ActionEvent evt) {

        Date date;
        try {
            date = (Date) Formats.TIMESTAMP.parseValue(jTxtStartDate.getText());
        } catch (BasicException e) {
            date = null;
        }
        date = JCalendarDialog.showCalendarTimeHours(this, date);
        if (date != null) {
            jTxtStartDate.setText(Formats.TIMESTAMP.formatValue(date));
        }
    }

    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {
        printZReport();
    }

    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {
        printXReport();
    }

    private void jButton9ActionPerformed(java.awt.event.ActionEvent evt) {
        String comando = "", flags = "", value = "";

        flags = this.jSpinner1.getValue().toString();
        value = this.jSpinner2.getValue().toString();

        while (flags.length() < 2) {
            flags = "0" + flags;
        }

        while (value.length() < 2) {
            value = "0" + value;
        }

        comando = "PJ" + flags + value;
        log("Flags = " + comando);
        try {
            this.tf.SendCmd(comando);
        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        jPanel4 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jTextField2 = new javax.swing.JTextField();
        jTextField1 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jTextField5 = new javax.swing.JTextField();
        jButton3 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jTextField6 = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jTextField7 = new javax.swing.JTextField();
        jPanel3 = new javax.swing.JPanel();
        jTextField8 = new javax.swing.JTextField();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        jRadioButton3 = new javax.swing.JRadioButton();
        jButton4 = new javax.swing.JButton();
        jRadioButton5 = new javax.swing.JRadioButton();
        jRadioButton6 = new javax.swing.JRadioButton();
        jRadioButton4 = new javax.swing.JRadioButton();
        jPanel6 = new javax.swing.JPanel();
        jButton2 = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        jTxtStartDate = new javax.swing.JTextField();
        jPanel7 = new javax.swing.JPanel();
        jButton5 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jTextField9 = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        jTextField10 = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        jTextField11 = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jTextField12 = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jTextField13 = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jTextField14 = new javax.swing.JTextField();
        jTextField15 = new javax.swing.JTextField();
        jTextField16 = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        jButton6 = new javax.swing.JButton();
         jButton7 = new javax.swing.JButton();
         jButton8 = new javax.swing.JButton();
         jButton9 = new javax.swing.JButton();
        jTextField17 = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jTextField18 = new javax.swing.JTextField();
        jLabel16 = new javax.swing.JLabel();
        jTextField19 = new javax.swing.JTextField();
        jTextField20 = new javax.swing.JTextField();
        jTextField21 = new javax.swing.JTextField();
        jTextField22 = new javax.swing.JTextField();
        jTextField23 = new javax.swing.JTextField();
        jTextField24 = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
         jLabel22 = new javax.swing.JLabel();
         jLabel23 = new javax.swing.JLabel();
         jLabel24 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        jSpinner1 = new javax.swing.JSpinner();
        jSpinner2 = new javax.swing.JSpinner();
         jTxtStartDate = new javax.swing.JTextField();
         btnDateStart = new javax.swing.JButton();

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuración de los Medios de Pago:"));

        jLabel3.setText("CESTA TICKET:");

        jLabel4.setText("CHEQUE:");

        jLabel1.setText("EFECTIVO: ");

        jLabel5.setText("TARJETA DEBITO:");

        jTextField2.setEditable(false);
        jTextField2.setText("02");

        jTextField1.setEditable(false);
        jTextField1.setText("01");

        jTextField3.setEditable(false);
        jTextField3.setText("05");

        jTextField4.setEditable(false);
        jTextField4.setText("09");

        jLabel6.setText("TARJETA CREDITO:");

        jTextField5.setEditable(false);
        jTextField5.setText("13");

        jButton3.setText("Enviar");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(30, 30, 30)
                        .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(jPanel4Layout.createSequentialGroup()
                                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel3)
                                    .add(jLabel4)
                                    .add(jLabel1)
                                    .add(jLabel5))
                                .add(66, 66, 66)
                                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                                    .add(jTextField4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(jTextField3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(jTextField1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(jTextField2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                            .add(jPanel4Layout.createSequentialGroup()
                                .add(jLabel6)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(jTextField5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(99, 99, 99)
                        .add(jButton3)))
                .addContainerGap(85, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(jTextField1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(jTextField2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(jTextField3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(jTextField4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel6)
                    .add(jTextField5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(jButton3)
                .addContainerGap(13, Short.MAX_VALUE))
        );

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasas de Impuesto:"));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasa 1"));

        jTextField6.setText("12.00");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jTextField6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(21, 21, 21))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jTextField6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(22, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasa 2"));

        jTextField7.setText("08.00");

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jTextField7, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 39, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jTextField7, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(22, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasa 3"));

        jTextField8.setText("22.00");

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jTextField8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 34, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jTextField8, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(22, Short.MAX_VALUE))
        );

        buttonGroup1.add(jRadioButton1);
        jRadioButton1.setSelected(true);
        jRadioButton1.setText("Excluido");

        buttonGroup1.add(jRadioButton2);
        jRadioButton2.setText("Incluido");

        buttonGroup2.add(jRadioButton3);
        jRadioButton3.setSelected(true);
        jRadioButton3.setText("Excluido");

        jButton4.setText("Enviar");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        buttonGroup2.add(jRadioButton5);
        jRadioButton5.setText("Incluido");

        buttonGroup3.add(jRadioButton6);
        jRadioButton6.setText("Incluido");
        jRadioButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton6ActionPerformed(evt);
            }
        });

        buttonGroup3.add(jRadioButton4);
        jRadioButton4.setSelected(true);
        jRadioButton4.setText("Excluido");

        org.jdesktop.layout.GroupLayout jPanel5Layout = new org.jdesktop.layout.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel5Layout.createSequentialGroup()
                        .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jRadioButton1)
                            .add(jRadioButton2))
                        .add(10, 10, 10)
                        .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(jRadioButton5)
                            .add(jRadioButton3))
                        .add(18, 18, 18)
                        .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jRadioButton6)
                            .add(jRadioButton4)))
                    .add(jPanel5Layout.createSequentialGroup()
                        .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(jButton4)
                            .add(jPanel5Layout.createSequentialGroup()
                                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(7, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jRadioButton1)
                    .add(jRadioButton4)
                    .add(jRadioButton3))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jRadioButton2)
                    .add(jRadioButton6)
                    .add(jRadioButton5))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 23, Short.MAX_VALUE)
                .add(jButton4)
                .addContainerGap())
        );

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Reportes"));

        jButton2.setText("Imprimir Programacion");
        jButton2.setBorderPainted(false);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton1.setText("Programar Fecha-Hora");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

       btnDateStart.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/date.png"))); // NOI18N
         btnDateStart.addActionListener(new java.awt.event.ActionListener() {
             public void actionPerformed(java.awt.event.ActionEvent evt) {
                 btnDateStartActionPerformed(evt);
             }
         });
         
         Calendar calendario= Calendar.getInstance();  
         
         jTxtStartDate.setText(Formats.TIMESTAMP.formatValue(calendario.getTime()));

        org.jdesktop.layout.GroupLayout jPanel6Layout = new org.jdesktop.layout.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel6Layout.createSequentialGroup()
                        .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jButton2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 143, Short.MAX_VALUE)
                            .add(jButton1))
                        .addContainerGap())
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel6Layout.createSequentialGroup()
                        .add(jTxtStartDate, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 125, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                         .addPreferredGap(LayoutStyle.RELATED)
                         .add(btnDateStart, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(38, 38, 38))))
        ); 
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .add(20, 20, 20)
                .add(jButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(38, 38, 38)
                .add(jButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE)
                .add(jTxtStartDate, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                 .add(btnDateStart)
                .addContainerGap())
        );

        jPanel7.setBorder(javax.swing.BorderFactory.createTitledBorder("Encabezado de Ticket:"));

        jButton5.setText("Enviar");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        jLabel2.setText("Razón Social:");

        jTextField9.setText("OpenBravo POS C.A");

        jLabel7.setText("Dirección 1:");

        jTextField10.setText("Av. Bolivar, Calle 8");

        jLabel8.setText("Dirección 2:");

        jTextField11.setText("La Morita - Edo. Miranda");

        jLabel9.setText("Telefono 1:");

        jTextField12.setText("0212-3528985");

        jLabel10.setText("Telefono 2:");

        jTextField13.setText("0416-8542200");

        jLabel11.setText("Linea Extra 1:");

        jLabel12.setText("Linea Extra 2:");

        jLabel13.setText("Linea Extra 3:");

        org.jdesktop.layout.GroupLayout jPanel7Layout = new org.jdesktop.layout.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel7Layout.createSequentialGroup()
                        .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel2)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel7)
                                .add(12, 12, 12)
                                .add(jTextField10, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel8)
                                .add(12, 12, 12)
                                .add(jTextField11, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel9)
                                .add(13, 13, 13)
                                .add(jTextField12, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel10)
                                .add(13, 13, 13)
                                .add(jTextField13, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel11)
                                .add(13, 13, 13)
                                .add(jTextField14, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel7Layout.createSequentialGroup()
                                .add(jLabel12)
                                .add(13, 13, 13)
                                .add(jTextField15, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE))
                            .add(jPanel7Layout.createSequentialGroup()
                                .add(jLabel13)
                                .add(13, 13, 13)
                                .add(jTextField16, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE)))
                        .addContainerGap())
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel7Layout.createSequentialGroup()
                        .add(jButton5)
                        .add(107, 107, 107))))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel7Layout.createSequentialGroup()
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel7)
                    .add(jTextField10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel8)
                    .add(jTextField11, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel9)
                    .add(jTextField12, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel10)
                    .add(jTextField13, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel11)
                    .add(jTextField14, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField15, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel12))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel13))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jButton5)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("Pie de Ticket:"));

        jButton6.setText("Enviar");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });

        jTextField17.setText("¡Hasta Pronto!");

        jLabel14.setText("Mensaje 1:");

        jLabel15.setText("Mensaje 2:");

        jTextField18.setText("Gracias...");

        jLabel16.setText("Mensaje 3:");

        jLabel17.setText("Mensaje 4:");

        jLabel18.setText("Mensaje 5:");

        jLabel19.setText("Mensaje 6:");

        jLabel20.setText("Mensaje 7:");

        jLabel21.setText("Mensaje 8:");

        org.jdesktop.layout.GroupLayout jPanel8Layout = new org.jdesktop.layout.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel8Layout.createSequentialGroup()
                        .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel14)
                                .add(6, 6, 6)
                                .add(jTextField17, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 162, Short.MAX_VALUE))
                            .add(jPanel8Layout.createSequentialGroup()
                                .add(jLabel15)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField18, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(jPanel8Layout.createSequentialGroup()
                                .add(jLabel16)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField19, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel17)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField20, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel18)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField21, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel19)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField22, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel20)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField23, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                                .add(jLabel21)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jTextField24, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)))
                        .addContainerGap())
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                        .add(jButton6)
                        .add(62, 62, 62))))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel8Layout.createSequentialGroup()
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel14))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel15)
                    .add(jTextField18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel16)
                    .add(jTextField19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel17))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel18))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel19))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel20))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jTextField24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel21))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jButton6)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel9.setBorder(javax.swing.BorderFactory.createTitledBorder("Otros"));
        jPanel9.setEnabled(false);
        jPanel9.setFocusTraversalPolicyProvider(true);
        
        jButton7.setText("Reporte Z Directo");

        jButton8.setText("Reporte X");
        
        jButton9.setText("Enviar");
        
        if(this.indSincrono)
        {jButton7.setEnabled(false);}
        
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });
        
        jButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton9ActionPerformed(evt);
            }
        });

        jSpinner1.setModel(new javax.swing.SpinnerNumberModel(0, 0, 50, 2));

        jSpinner2.setModel(new javax.swing.SpinnerNumberModel(0, 0, 5, 1));

        jLabel22.setText("Programar Flags");

        jLabel23.setText("Flags");

        jLabel24.setText("Valor");

        org.jdesktop.layout.GroupLayout jPanel9Layout = new org.jdesktop.layout.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
            .add(jPanel9Layout.createSequentialGroup()
                .add(jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
                    .add(jPanel9Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jPanel9Layout.createParallelGroup(GroupLayout.TRAILING, false)
                            .add( jButton8, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add( jButton7, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .add(jPanel9Layout.createSequentialGroup()
                        .add(24, 24, 24)
                        .add(jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
                            .add(jLabel22)
                            .add(jPanel9Layout.createSequentialGroup()
                                .add(jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
                                    .add(jSpinner1, GroupLayout.PREFERRED_SIZE, 39, GroupLayout.PREFERRED_SIZE)
                                    .add(jLabel23))
                                .addPreferredGap(LayoutStyle.RELATED, 46, Short.MAX_VALUE)
                                .add(jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
                                    .add(jLabel24)
                                    .add(jSpinner2, GroupLayout.PREFERRED_SIZE, 39, GroupLayout.PREFERRED_SIZE)
                                    .add( jButton9, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))
                .addContainerGap(27, Short.MAX_VALUE))
        );
        
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(GroupLayout.LEADING)
            .add(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .add(jButton7)
                .addPreferredGap(LayoutStyle.UNRELATED)
                .add(jButton8)
                .add(25, 25, 25)
                .add(jLabel22)
                .addPreferredGap(LayoutStyle.UNRELATED)
                .add(jPanel9Layout.createParallelGroup(GroupLayout.BASELINE)
                    .add(jLabel23)
                    .add(jLabel24))
                .addPreferredGap(LayoutStyle.RELATED)
                .add(jPanel9Layout.createParallelGroup(GroupLayout.BASELINE)
                    .add(jSpinner1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .add(jSpinner2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(LayoutStyle.RELATED)
                    .add(jPanel9Layout.createParallelGroup(GroupLayout.BASELINE)
                    .add(jButton9))
                .addContainerGap(130, Short.MAX_VALUE))
        );


        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel7, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(24, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel4, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                        .add(jPanel8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(jPanel7, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .add(28, 28, 28))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        String dia = jTxtStartDate.getText().substring(0, 2);
        String mes = jTxtStartDate.getText().substring(3, 4);
        String ano = jTxtStartDate.getText().substring(6, 9);
        String hor = jTxtStartDate.getText().substring(11, 13);
        String min = jTxtStartDate.getText().substring(14, 15);
        String seg = jTxtStartDate.getText().substring(17, 18);
        try {
            // Grabo la Hora
            this.tf.SendCmd("PF" + hor + min + seg);
            // Grabo la fecha
            this.tf.SendCmd("PG" + dia + mes + ano);

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // TODO add your handling code here:
        try {
            this.tf.SendCmd("D");
        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        } catch (java.lang.RuntimeException re) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jRadioButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton6ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jRadioButton6ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // Prog. Medios de Pagos       
        try {
            String sCmd = "PE01EFECTIVO      ";
            boolean rep = this.tf.SendCmd(sCmd);

            sCmd = "PE02CESTA TICKET  ";
            rep &= this.tf.SendCmd(sCmd);
            sCmd = "PE05CHEQUE        ";
            rep &= this.tf.SendCmd(sCmd);
            sCmd = "PE09TARJT DEBITO  ";
            rep &= this.tf.SendCmd(sCmd);
            sCmd = "PE13TARJT CREDITO ";
            rep &= this.tf.SendCmd(sCmd);

            if (!rep) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            }

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detecto la Impresora Fiscal. Verifique que este encendida y/o conectada. \n" + pe.getMessage()));
        }

    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        // Prog. Tasas de Impuestos       
        try {
            String sCmd = "PT";
            String t1, t2, t3, vt1, vt2, vt3;

            if (this.jRadioButton1.isSelected()) {
                t1 = "1";
            } else {
                t1 = "2";
            }

            if (this.jRadioButton3.isSelected()) {
                t2 = "1";
            } else {
                t2 = "2";
            }

            if (this.jRadioButton4.isSelected()) {
                t3 = "1";
            } else {
                t3 = "2";
            }

            vt1 = jTextField6.getText().replace(".", "");
            vt2 = jTextField7.getText().replace(".", "");
            vt3 = jTextField8.getText().replace(".", "");

            sCmd += t1 + vt1 + t2 + vt2 + t3 + vt3;

            boolean rep = this.tf.SendCmd(sCmd);

            rep &= this.tf.SendCmd("Pt");

            if (!rep) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            }

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detecto la Impresora Fiscal. Verifique que este encendida y/o conectada. \n" + pe.getMessage()));
        }
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        // Prog. Encabezados de ticket       
        try {
            String sCmd = "PH";

            boolean rep = this.tf.SendCmd(sCmd + "01" + jTextField9.getText());

            rep &= this.tf.SendCmd(sCmd + "02" + jTextField10.getText());
            rep &= this.tf.SendCmd(sCmd + "03" + jTextField11.getText());
            rep &= this.tf.SendCmd(sCmd + "04" + jTextField12.getText());
            rep &= this.tf.SendCmd(sCmd + "05" + jTextField13.getText());
            rep &= this.tf.SendCmd(sCmd + "06" + jTextField14.getText());
            rep &= this.tf.SendCmd(sCmd + "07" + jTextField15.getText());
            rep &= this.tf.SendCmd(sCmd + "08" + jTextField16.getText());

            if (!rep) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            }

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detecto la Impresora Fiscal. Verifique que este encendida y/o conectada. \n" + pe.getMessage()));
        }
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        // Prog. Pie de ticket       
        try {
            String sCmd = "PH";

            boolean rep = this.tf.SendCmd(sCmd + "91" + jTextField17.getText());

            rep &= this.tf.SendCmd(sCmd + "92" + jTextField18.getText());
            rep &= this.tf.SendCmd(sCmd + "93" + jTextField19.getText());
            rep &= this.tf.SendCmd(sCmd + "94" + jTextField20.getText());
            rep &= this.tf.SendCmd(sCmd + "95" + jTextField21.getText());
            rep &= this.tf.SendCmd(sCmd + "96" + jTextField22.getText());
            rep &= this.tf.SendCmd(sCmd + "97" + jTextField23.getText());
            rep &= this.tf.SendCmd(sCmd + "98" + jTextField24.getText());

            if (!rep) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));
            }

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detecto la Impresora Fiscal. Verifique que este encendida y/o conectada. \n" + pe.getMessage()));
        }
    }//GEN-LAST:event_jButton6ActionPerformed

    public boolean getFondoRetiro(String transaccion, double monto) {
        // Fondo Retiro
        boolean rep = false;

        try {
            String sCmd = "";

            if (transaccion.equals("cashin")) {
                sCmd = "9101";
            } else {
                monto = (-1) * monto;
                sCmd = "9001";
            }

            String precio = String.valueOf(monto);
            String[] Precio_def = partir_valor(precio);

            // Obtengo la trama entera del precio
            while (Precio_def[0].length() < 10) {
                Precio_def[0] = "0" + Precio_def[0];
            }
            // Obtengo la trama decimal del precio
            if (Precio_def[1].length() > 2) {
                Precio_def[1] = Precio_def[1].substring(0, 2);
            } else {
                while (Precio_def[1].length() < 2) {
                    Precio_def[1] = Precio_def[1] + "0";
                }
            }

            rep = this.tf.SendCmd(sCmd + Precio_def[0] + Precio_def[1]);

            rep &= this.tf.SendCmd("t");

            if (!rep) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. " + this.tf.Estado));

            }

        } catch (PrinterException pe) {
            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " No se detecto la Impresora Fiscal. Verifique que este encendida y/o conectada. \n" + pe.getMessage()));
        }

        return rep;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JRadioButton jRadioButton3;
    private javax.swing.JRadioButton jRadioButton4;
    private javax.swing.JRadioButton jRadioButton5;
    private javax.swing.JRadioButton jRadioButton6;
    private javax.swing.JSpinner jSpinner1;
    private javax.swing.JSpinner jSpinner2;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField10;
    private javax.swing.JTextField jTextField11;
    private javax.swing.JTextField jTextField12;
    private javax.swing.JTextField jTextField13;
    private javax.swing.JTextField jTextField14;
    private javax.swing.JTextField jTextField15;
    private javax.swing.JTextField jTextField16;
    private javax.swing.JTextField jTextField17;
    private javax.swing.JTextField jTextField18;
    private javax.swing.JTextField jTextField19;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField20;
    private javax.swing.JTextField jTextField21;
    private javax.swing.JTextField jTextField22;
    private javax.swing.JTextField jTextField23;
    private javax.swing.JTextField jTextField24;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JTextField jTextField7;
    private javax.swing.JTextField jTextField8;
    private javax.swing.JTextField jTextField9;
    private javax.swing.JTextField jTxtStartDate;
    private javax.swing.JButton btnDateStart;

    // End of variables declaration//GEN-END:variables
	private String FormatearNormal(double value) {
        java.text.NumberFormat form = java.text.NumberFormat.getInstance();

        return form.format(com.openbravo.format.DoubleUtils.fixDecimals((Number) value));

    }

    @Override
    public void printLine(String sproduct, double dprice, double dunits, int taxinfo) {

        if (this.MyMaq.getTasas()[taxinfo].isCascade()) {
            totalAcumuladoSystem += (dprice * dunits) * (1.0 - this.MyMaq.getTasas()[taxinfo].getRate());
        } else {
            totalAcumuladoSystem += (dprice * dunits) * (1.0 + this.MyMaq.getTasas()[taxinfo].getRate());
        }

        String trama = "";
        String precio = FormatearNormal(dprice);
        String tasa = String.valueOf(taxinfo);
        String unidad = FormatearNormal(dunits);
        precio = precio.replace(".", "");
        unidad = unidad.replace(".", "");
        precio = precio.replace(",", ".");
        unidad = unidad.replace(",", ".");

        String[] Precio_def = partir_valor(precio);

        if (dunits < 0) {
            unidad = unidad.replace("-", "");
            this.isDevolucion = true;
        }
        String[] Cantidad_def = partir_valor(unidad);
        // Obtengo la trama entera del precio
        while (Precio_def[0].length() < 8) {
            Precio_def[0] = "0" + Precio_def[0];
        }
        // Obtengo la trama decimal del precio
        if (Precio_def[1].length() > 2) {
            Precio_def[1] = Precio_def[1].substring(0, 2);
        } else {
            while (Precio_def[1].length() < 2) {
                Precio_def[1] = Precio_def[1] + "0";
            }
        }

        // Obtengo la parte entera de la unidades
        while (Cantidad_def[0].length() < 5) {
            Cantidad_def[0] = "0" + Cantidad_def[0];
        }
        //Obtengo la parte decimal de la cantidad
        if (Cantidad_def[1].length() > 3) {
            Cantidad_def[1] = Cantidad_def[1].substring(0, 3);
        } else {

            while (Cantidad_def[1].length() < 3) {
                Cantidad_def[1] = Cantidad_def[1] + "0";
            }
        }

        char tax = ' ';
        if (!isDevolucion) {
            if (tasa.equals("0")) {
                tax = ' ';
            } else if (tasa.equals("1")) {
                tax = '!';
            } else if (tasa.equals("2")) {
                tax = '"';
            } else if (tasa.equals("3")) {
                tax = '#';
            }
        } else {
            if (tasa.equals("0")) {
                tax = '0';
            } else if (tasa.equals("1")) {
                tax = '1';
            } else if (tasa.equals("2")) {
                tax = '2';
            } else if (tasa.equals("3")) {
                tax = '3';
            }
        }

        if (!isDevolucion) {
            trama = tax + Precio_def[0] + Precio_def[1] + Cantidad_def[0] + Cantidad_def[1] + sproduct;
        } else {
            trama = "d" + tax + Precio_def[0] + Precio_def[1] + Cantidad_def[0] + Cantidad_def[1] + sproduct;
        }

        log("Precio = " + precio + "  Cantidad  = " + unidad + " Tasa  =  " + tasa + "  Producto = " + sproduct);
        log("Trama =" + trama);

        try {
            boolean trans = this.tf.SendCmd(trama);

            if (!trans) {

                this.tf.getS2PrinterData();
                String montoFormat = FormatearNormal(totalAcumuladoSystem);
                montoFormat = montoFormat.replaceAll(".", "");
                montoFormat = montoFormat.replaceAll(",", ".");
                String[] montoSystem = this.partir_valor(montoFormat);

                while (montoSystem[1].length() < 2) {
                    montoSystem[1] = montoSystem[1] + "0";
                }

                String acumuladoTotalSystem = montoSystem[0] + "." + montoSystem[1].substring(0, 2);
                log("Monto en Sistema = " + acumuladoTotalSystem + " :  Monto en Impresora Fiscal = " + S2Estado.getAmountToPay());

                //Verifico Status y Error
                this.tf.getPrinterStatus();

                log(" Status = " + StatusErrorPrinter.getPrinterStatusCode() + " Error = " + StatusErrorPrinter.getPrinterErrorCode());

                if (S2Estado.getAmountToPay() != Double.valueOf(acumuladoTotalSystem)) {

                    log("No se registro la ultima linea");
                    isDevolucion = false;
                    if (this.indexPLU_Reg > 0) {
                        boolean isAnula = this.tf.SendCmd("7");
                        if (isAnula) {
                            this.MyMaq.ModificarContadores();
                        }
                    }
                    totalAcumuladoSystem = 0.0;
                    this.indexPLU_Reg = 0;

                    throw new PrinterException("Error en Transacción al registrar item.", StatusErrorPrinter);
                }
            } else {
                ++this.indexPLU_Reg;
            }

        } catch (PrinterException pe) {

            log("No se registro la ultima linea");
            isDevolucion = false;
//            if (this.indexPLU_Reg > 0) {
//                boolean isAnula = this.tf.SendCmd("7");
//                if (isAnula) {
//                    this.MyMaq.ModificarContadores();
//                }
//            }
            totalAcumuladoSystem = 0.0;
            this.indexPLU_Reg = 0;

            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal. Verifique el estado y conexion de la impresora fiscal." + this.tf.Estado));
            // Anular el ticket en el emulador interno.
        } catch (java.lang.RuntimeException re) {
            isDevolucion = false;
            log("No se registro la ultima linea.  " + re.getMessage());
//	        	if(this.indexPLU_Reg > 0) {  
//                            boolean isAnula = this.tf.SendCmd("7");
//                            if(isAnula) {
//                            this.MyMaq.ModificarContadores();
//                            }
//                        }
            totalAcumuladoSystem = 0.0;
            this.indexPLU_Reg = 0;

            JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_CAUTION, " Error de Envio de datos a la Impresora Fiscal.   Verifique el estado y conexion de la impresora fiscal." + this.tf.Estado));
            // Anular el ticket en el emulador interno.
        }

    }
}
