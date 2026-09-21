# Astrid — Assistant IA Flottant

Une appli Android avec une **bulle flottante** (comme Messenger) que tu peux glisser n'importe où sur ton écran. Un appui l'ouvre en petit panneau de chat pour discuter avec Claude ou GPT.

## 1. Mettre le projet sur GitHub

1. Crée un nouveau dépôt sur GitHub (public ou privé), par exemple `floating-ai-assistant`.
2. Depuis ce dossier, sur ton ordinateur ou via GitHub Desktop / l'appli GitHub :
   ```bash
   git init
   git add .
   git commit -m "Premier commit"
   git branch -M main
   git remote add origin https://github.com/TON-PSEUDO/floating-ai-assistant.git
   git push -u origin main
   ```
   (Tu peux aussi glisser-déposer le dossier via l'interface web de GitHub si tu préfères éviter la ligne de commande.)

## 2. Laisser GitHub compiler l'APK pour toi

Le dépôt contient déjà un fichier `.github/workflows/build-apk.yml`. Dès que tu pousses (`push`) sur la branche `main`, GitHub Actions compile automatiquement l'application.

1. Va dans l'onglet **Actions** de ton dépôt GitHub.
2. Ouvre le run le plus récent ("Build APK").
3. Attends qu'il finisse (2-5 minutes, icône verte ✅).
4. Descends jusqu'à **Artifacts** et télécharge `app-debug-apk` (fichier .zip contenant `app-debug.apk`).

## 3. Installer l'APK sur ton téléphone

1. Transfère le fichier `app-debug.apk` sur ton téléphone (via Google Drive, e-mail, câble USB, etc.) et ouvre-le.
2. Android va demander d'autoriser l'installation depuis cette source ("Installer des applis inconnues") — accepte.
3. Installe l'appli.

## 4. Configurer et activer la bulle

1. Ouvre l'appli **Assistant IA Flottant**.
2. Choisis le fournisseur (Anthropic ou OpenAI), colle ta clé API, vérifie le nom du modèle, puis appuie sur **Enregistrer**.
3. Appuie sur **Activer la bulle flottante** → Android t'amène dans les réglages pour autoriser "l'affichage par-dessus les autres applis" → active-le → reviens dans l'appli → appuie à nouveau sur **Activer la bulle flottante**.
4. Une bulle violette apparaît sur ton écran, au-dessus de tes autres applis. Glisse-la pour la déplacer, appuie dessus pour ouvrir le chat.

## Où obtenir une clé API gratuite

- **Google Gemini (gratuit)** : aistudio.google.com/apikey — connecte-toi avec un compte Google, "Create API key". Aucune carte bancaire requise.
- **Groq (gratuit, très rapide)** : va sur **console.groq.com/keys**, connecte-toi (Google ou e-mail), clique sur "Create API Key". Aucune carte bancaire requise. Fait tourner des modèles open source (Llama 3.3 par défaut dans l'appli) sur du matériel très rapide — réponses quasi instantanées. Limite quotidienne généreuse pour un usage personnel.
- **Anthropic (Claude)** : console.anthropic.com, section "API Keys" — payant à l'usage, pas de tier gratuit permanent.
- **OpenAI (GPT)** : platform.openai.com, section "API keys" — payant à l'usage, pas de tier gratuit permanent.

Ta clé reste stockée uniquement sur ton téléphone (SharedPreferences locales) — elle n'est jamais envoyée à GitHub ni à personne d'autre que le fournisseur d'IA choisi.

## Limites à connaître

- La bulle reste au-dessus des autres applis tant que le téléphone est allumé et l'appli active en arrière-plan — mais elle **n'apparaît pas sur l'écran verrouillé** (Android ne le permet pas pour ce type de fenêtre).
- Certains fabricants (Xiaomi, Huawei, Oppo...) limitent les services en arrière-plan par défaut : si la bulle disparaît après un moment, va dans Réglages > Batterie > Assistant IA Flottant et désactive l'optimisation de batterie pour cette appli.
- Ceci est un build **debug**, suffisant pour un usage personnel. Pas besoin de signature particulière pour l'installer sur ton propre téléphone.
