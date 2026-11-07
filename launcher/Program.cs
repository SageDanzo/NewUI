using System.ComponentModel;
using System.Diagnostics;
using System.Windows.Forms;

namespace AltosModernLauncher;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        var appDirectory = AppContext.BaseDirectory;
        var javaPath = Path.Combine(appDirectory, "runtime", "bin", "javaw.exe");
        var jarPath = Path.Combine(appDirectory, "altosui-redesigned.jar");

        if (!File.Exists(javaPath) || !File.Exists(jarPath))
        {
            MessageBox.Show(
                "The bundled Java runtime or AltOS application files are missing. Rebuild or extract the complete AltOS Modern package.",
                "AltOS Modern",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return 1;
        }

        var startInfo = new ProcessStartInfo
        {
            FileName = javaPath,
            WorkingDirectory = appDirectory,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        startInfo.ArgumentList.Add($"-Djava.library.path={appDirectory}");
        startInfo.ArgumentList.Add($"-Daltos.recovery.bridge={Path.Combine(appDirectory, "recovery-bridge", "publish", "AltosRecoveryBridge.exe")}");
        startInfo.ArgumentList.Add("-jar");
        startInfo.ArgumentList.Add(jarPath);

        foreach (var argument in args)
            startInfo.ArgumentList.Add(argument);

        try
        {
            if (Process.Start(startInfo) is null)
            {
                MessageBox.Show(
                    "Windows could not start AltOS.",
                    "AltOS Modern",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                return 1;
            }
        }
        catch (Win32Exception exception)
        {
            MessageBox.Show(
                $"Could not start AltOS: {exception.Message}",
                "AltOS Modern",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return 1;
        }

        return 0;
    }
}
