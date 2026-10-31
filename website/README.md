# GuideLight website

Static, dependency-free product website for GuideLight 0.22.3.

## Preview

From this folder, run any static server, for example:

```powershell
npx serve .
```

## APK download

The repository keeps a local copy of the current APK at:

```text
downloads/GuideLight-0.22.3.apk
```

The published download is stored in the public `guidelight-apk-downloads` Vercel Blob store because the APK is 180.5 MB and Vercel's Hobby plan limits CLI source uploads to 100 MB. The live file is:

```text
https://vkhzo6bw9repcvrg.public.blob.vercel-storage.com/releases/GuideLight-0.22.3.apk
```

The local `downloads/` directory is excluded from Vercel source uploads by `.vercelignore`.

Keep the displayed SHA-256 in sync with the file. The current checksum is:

```text
3111f4a090af695ca695f6f8cce3456f1383b9c678d09f467f7184fc6928ee58
```

## Deploy to Vercel

Set the Vercel project Root Directory to `website`. This site has no build command and no output directory; Vercel serves the static files directly.
