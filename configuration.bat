cd C:\Users\MaxDev\AndroidStudioProjects\AmbulantPoint

# Inicializar repositorio local
git init

# Agregar todos los archivos
git add .

# Primer commit
git commit -m "Initial commit: AmbulantPoint M1 (Gestión de Catálogo) with skeleton activities for M2-M4"

# Conectar con GitHub
git remote add origin https://github.com/MaxDev8459/AmbulantPoint.git

# Cambiar rama principal a 'main' (si aún está en 'master')
git branch -M main

# Hacer push inicial
git push -u origin main