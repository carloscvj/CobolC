/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cobolc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;

/**
 *
 * @author carlos
 */
public class CobolC extends Task implements Serializable {

    private final List<FileSet> filesets = new ArrayList();
    private String srcDir = "src";
    private String buildDir = "dist/exe";
    private String debugDir = "debug/exe";
    private String cobcompiler = "/usr/bin/cobol";
    private String cobdir = "/opt/lib/cobol";
    private String ld_library_path = "/opt/lib/cobol/coblib";
    private String cobcpy;

    private String srcToExt(String cbls, String ext) {
        int pos = cbls.lastIndexOf(".CBL");
        String ints = cbls.substring(0, pos);
        return ints + ext;
    }

    private String srcToBuild(String ints) {
        ints = ints.replaceAll(getSrcDir(), getBuildDir());
        return ints;
    }

    private String srcToDebug(String ints) {
        ints = ints.replaceAll(getSrcDir(), getDebugDir());
        return ints;
    }

    private void cpToDebug(File src) {
        if (src.exists()) {
            InputStream in = null;
            try {
                File f = new File(srcToDebug(src.getAbsolutePath()));
                f.getParentFile().mkdirs();
                FileResource fri = new FileResource(src);
                FileResource fro = new FileResource(f);
                in = fri.getInputStream();
                OutputStream ou = fro.getOutputStream();
                int c;
                while ((c = in.read()) > -1) {
                    ou.write(c);
                }

            } catch (IOException ex) {
                Logger.getLogger(CobolC.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(CobolC.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void mvToDebug(File src) {
        if (src.exists()) {
            File f = new File(srcToDebug(src.getAbsolutePath()));
            f.getParentFile().mkdirs();
            src.renameTo(f);
        }
    }

    private void mvToBuild(File src) {
        if (src.exists()) {
            File f = new File(srcToBuild(src.getAbsolutePath()));
            f.getParentFile().mkdirs();
            src.renameTo(f);
        }
    }
    private boolean ter1 = false;
    private boolean ter2 = false;

    @SuppressWarnings("SleepWhileInLoop")
    private void espera(Process proceso) throws InterruptedException {
        ter1 = false;
        ter2 = false;
        final BufferedReader errs = new BufferedReader(new InputStreamReader(proceso.getErrorStream()));
        final BufferedReader inps = new BufferedReader(new InputStreamReader(proceso.getInputStream()));
        Thread terr;
        terr = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = errs.readLine()) != null) {
                        log(line, Project.MSG_ERR);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(CobolC.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    ter1 = true;
                }
            }
        });
        terr.start();
        Thread tinp = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = inps.readLine()) != null) {
                        log(line);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(CobolC.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    ter2 = true;
                }
            }
        });
        tinp.start();

        while (!ter1) {
            Thread.sleep(300);
        }
        while (!ter2) {
            Thread.sleep(300);
        }

    }

    private String[] getEnv() {
        Properties prop = new Properties();
        prop.setProperty("COBDIR", getCobdir());
        prop.setProperty("LD_LIBRARY_PATH", getLd_library_path());
        prop.setProperty("COBCPY", getCobcpy());
        Object hay[] = prop.entrySet().toArray();
        String ret[] = new String[hay.length];
        for (int i = 0; i < hay.length; i++) {
            ret[i] = hay[i].toString();
        }
        return ret;
    }

    @Override
    public void execute() {


        //System.setProperty("COBDIR", getCobdir());
        //System.setProperty("LD_LIBRARY_PATH", getLd_library_path());
        //System.setProperty("COBCPY", getCobcpy());

        String cbls;
        String ints;

        log("-------------------------compilador CobolC-----------------------------------");
        String estoy = new File(".").getAbsolutePath();
        log("estoy en:" + estoy);

        for (FileSet unF : getFilesets()) {
            Iterator iter = unF.iterator();
            while (iter.hasNext()) {

                Object obj = iter.next();
                FileResource fcbl = (FileResource) obj;
                cbls = fcbl.getFile().getAbsolutePath();
                ints = srcToBuild(srcToExt(cbls, ".int"));
                FileResource fint = new FileResource(new File(ints));

                try {
                    if (fint.getLastModified() < fcbl.getLastModified()) {
                        log(fcbl.getFile().getAbsolutePath() + " to " + fint.getFile().getAbsolutePath());
                        Process proceso = Runtime.getRuntime().exec(getCobcompiler() + " " + fcbl.getFile().getAbsolutePath(), getEnv());
                        espera(proceso);
                        proceso.waitFor();

                    }
                } catch (IOException ex) {
                    log(ex.toString(), Project.MSG_ERR);
                } catch (InterruptedException ex) {
                    Logger.getLogger(CobolC.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    //   cpToDebug(new File(srcToExt(cbls, ".CBL")));
                    mvToDebug(new File(srcToExt(cbls, ".lst")));
                    mvToDebug(new File(srcToExt(cbls, ".idy")));
                    cpToDebug(new File(srcToExt(cbls, ".int")));
                    mvToBuild(new File(srcToExt(cbls, ".int")));

                }
            }
        }
        setCobcpy(null);
        log("------------------fin de compilador CobolC-----------------------------------");
    }

    public void addFileset(FileSet fileset) {
        getFilesets().add(fileset);
    }

    public List<FileSet> getFilesets() {
        return filesets;
    }

    public String getCobcompiler() {
        return cobcompiler;
    }

    public void setCobcompiler(String cobcompiler) {
        this.cobcompiler = cobcompiler;
    }

    public String getCobdir() {
        return cobdir;
    }

    public void setCobdir(String cobdir) {
        this.cobdir = cobdir;
    }

    public String getLd_library_path() {
        return ld_library_path;
    }

    public void setLd_library_path(String ld_library_path) {
        this.ld_library_path = ld_library_path;
    }

    public String getCobcpy() {
        return cobcpy;
    }

    public void setCobcpy(String cobcpy) {
        this.cobcpy = cobcpy;
    }

    public String getSrcDir() {
        return srcDir;
    }

    public void setSrcDir(String srcDir) {
        this.srcDir = srcDir;
    }

    public String getBuildDir() {
        return buildDir;
    }

    public void setBuildDir(String buildDir) {
        this.buildDir = buildDir;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public void setDebugDir(String debugDir) {
        this.debugDir = debugDir;
    }
}
