#!groovy

// Gregory Pageot
// 2018-07-23

node
{
	try{
		// Perforce workspace mapping
		//		//DEPOT_NAME/UE4/Trunk/...	//PerforceWorkspaceRoot/UE4/Trunk/...
		//		//DEPOT_NAME/PROJECT_NAME/...	//PerforceWorkspaceRoot/UE4/Projects/PROJECT_NAME/...

		def projectLocalPath = PROJECT_LOCAL_PATH
		def projectName = PROJECT_NAME
		def engineLocalPath = ENGINE_LOCAL_PATH
		def perforceWorkspaceName = P4_WORKSPACE_NAME
		def perforceWorkspaceNameProject = P4_WORKSPACE_NAME_PROJECT
		def perforceCredentialInJenkins = JENKINS_P4_CREDENTIAL
		def perforceUnicodeMode = P4_UNICODE_ENCODING

		stage('Get perforce')
		{
			checkout perforce(
				credential: perforceCredentialInJenkins,
				populate: syncOnly(force: false, have: true, modtime: true, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, revert: true),
				workspace: staticSpec(charset: perforceUnicodeMode, name: perforceWorkspaceName, pinHost: false)
				)
		}

		stage('Get perforce(Project)')
		{
			if(perforceWorkspaceNameProject != "")
			{
				checkout perforce(
					credential: perforceCredentialInJenkins,
					populate: syncOnly(force: false, have: true, modtime: true, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, revert: true),
					workspace: staticSpec(charset: 'none', name: perforceWorkspaceNameProject, pinHost: false))
				
			}
		}

		def p4 = p4 credential: perforceCredentialInJenkins, workspace: staticSpec(charset: perforceUnicodeMode, name: perforceWorkspaceName, pinHost: false)

		stage( 'Prepare' )
		{
			// We need to use double quote for batch path in case engine path contains space
			// /D				Change drive at same time as current folder		

			// Generate Visual studio projects
			// WARNING : the file "Engine\Binaries\DotNET\UnrealBuildTool.xml" should have the filetype manually set to "text+w"
			bat """
				cd /D \"${engineLocalPath}\\Engine\\Build\\BatchFiles\"
				GenerateProjectFiles.bat -project=\"${projectLocalPath}\\${projectName}.uproject\" -game -rocket -progress
				"""
		}

		stage('Checkout')
		{
			p4.run('edit', 
				"${engineLocalPath}/Engine/Binaries/Win64/....exe".toString(),
				"${engineLocalPath}/Engine/Binaries/Win64/....dll".toString(),
				"${engineLocalPath}/Engine/Binaries/Win64/....pdb".toString(),
				"${engineLocalPath}/Engine/Binaries/Win64/....target".toString(),
				"${engineLocalPath}/Engine/Binaries/Win64/....modules".toString(),
				"${engineLocalPath}/Engine/Binaries/Win64/....version".toString(),
				"${engineLocalPath}/Engine/Binaries/DotNET/UnrealBuildTool.exe.config".toString(),
				"${engineLocalPath}/Engine/Plugins/.../Binaries/Win64/....dll".toString(),
				"${engineLocalPath}/Engine/Plugins/.../Binaries/Win64/....modules".toString(),
				"${projectLocalPath}/Binaries/Win64/....exe".toString(),
				"${projectLocalPath}/Binaries/Win64/....dll".toString(),
				"${projectLocalPath}/Binaries/Win64/....pdb".toString(),
				"${projectLocalPath}/Binaries/Win64/....target".toString(),
				"${projectLocalPath}/Binaries/Win64/....modules".toString(),
				"${projectLocalPath}/Binaries/Win64/....version".toString(),
				"${projectLocalPath}/Plugins/.../Binaries/Win64/....dll".toString(),
				"${projectLocalPath}/Plugins/.../Binaries/Win64/....pdb".toString(),
				"${projectLocalPath}/Plugins/.../Binaries/Win64/....modules".toString()
				)
		}

		stage( 'Compile' )
		{
			// Compile the game
			// Same as Visual studio NMake
			bat """
				cd /D \"${engineLocalPath}\\Engine\\Build\\BatchFiles\"
				Build.bat ${projectName}Editor Win64 Development -Project=\"${projectLocalPath}\\${projectName}.uproject\"
				"""
		}

		stage('Submit')
		{
			// TODO: The P4 task will get aborted after 5 minutes !
			// p4.run('submit', "-d "[ue4] ${env.JOB_NAME} ${env.BUILD_NUMBER}".toString())
		}

		slackSend color: 'good', message: "${env.JOB_NAME} ${env.BUILD_NUMBER} succeed (${env.BUILD_URL})"
	}
	catch (exception)
	{
		slackSend color: 'bad', message: "${env.JOB_NAME} ${env.BUILD_NUMBER} failed (${env.BUILD_URL})"
		throw exception
	}
}
