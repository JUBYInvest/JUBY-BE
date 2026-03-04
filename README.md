# JUBY - BACKEND Git Flow
## Clone && Push Work Flow
### a) Git Repository Clone
```
git clone https://github.com/JUBYInvest/JUBY-BE.git
cd JUBY-BE
```
### b) Create Branch 
```
git checkout -b feat/featureName
```
`ex)git checkout -b feat/pastInvest`
### c) After work done, Branch Push(What you did)
```
git push origin feat/featureName
```
`ex)git push origin feat/pastInvest`

## Merge Work Flow
### a) "IF MERGED" PULL dev branch -> local dev branch
```
git checkout dev
git pull origin dev
```
※If you are working or before commited, store your works and move to dev
```
git stash
git checkout dev
git pull origin dev
```
### b) After dev branch pushed into local, go to working branch and merge
```
git checkout feat/pastInvest
get merge dev
```
※If you have saved code, restore it
```
git checkout feat/pastInvest
git merge dev
git stash pop
```
