# About and app updates

Open **Settings → About** to see the installed Scriflow version. Select **Check
for updates** to check the project's GitHub releases. Checks are manual; opening
Settings does not contact GitHub. **Include preview releases** controls whether
alpha, beta and release-candidate versions are offered alongside stable releases.
The default includes previews when the installed version is already a preview.

When a newer release is available, **Download update** saves its installer in
Scriflow's private storage. Scriflow checks the published SHA-256 checksum,
package name, version, version code and signing certificate before offering
**Install update**. Android may first ask you to allow installations from
Scriflow, then presents its own installation confirmation. You can return to
Settings and try again if the download or installation is interrupted.

**Delete installer after a successful update** is enabled by default and can be
turned off. Cleanup occurs after Android reports a successful replacement; an
installer is kept when installation is canceled or fails. You can also use
**Remove saved installers** to remove retained update packages. These controls
do not delete your notebooks or files downloaded through the browser.
