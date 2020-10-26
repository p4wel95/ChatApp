'use strict'

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

exports.sendNotification = functions.database.ref('/Notifications/{receiver_user_id}/{notification_id}').onWrite((data, context) =>
{
	const receiver_user_id = context.params.receiver_user_id;
	const notification_id = context.params.notification_id;

	console.log('We have a notification to send to :' , receiver_user_id);

	if (!data.after.val())
	{
	 console.log('A notification has been deleted :' , notification_id);
	 return null;
	}

	const sender_user_id = admin.database().ref(`/Notifications/${receiver_user_id}/${notification_id}/from`).once('value');
	
	return sender_user_id.then(result =>
	{
		const senderId = result.val();
	    console.log('notification from:' ,senderId);
	    const userQuery = admin.database().ref(`/Users/${senderId}/name`).once('value');
		console.log('1');
	    return userQuery.then(result =>
		{
			console.log('2');
			const userName = result.val();
			console.log('username:' ,userName);
			const DeviceToken = admin.database().ref(`/Users/${receiver_user_id}/device_token`).once('value');

			return DeviceToken.then(result =>
			{
				const token_id = result.val();
				const payload =
				{
				    notification:
				   {
					title: "New Chat Request",
					body: `${userName} wants to connect with you.`,
					icon: "default",
					sound: "default"
				   }
				  };

				return admin.messaging().sendToDevice(token_id, payload).then(response =>
				{
						console.log('request sent to : ',token_id);
				});
			});
		});
	});
})

exports.sendNotification2 = functions.database.ref('/Messages/{receiver_user_id}/{sender_user_id}/{message_id}').onWrite((data, context) =>
{
	const receiver_user_id = context.params.receiver_user_id;
	const sender_user_id = context.params.sender_user_id;
	const message_id = context.params.message_id;

	
	
	if (!data.after.val())
	{
	 console.log('A notification has been deleted :');
	 return null;
	}
	
	const from = admin.database().ref(`/Messages/${receiver_user_id}/${sender_user_id}/${message_id}/from`).once('value');
	
	return from.then(result =>
	{
		const from = result.val();
		if (receiver_user_id === from)
		{
			console.log('spadam' , receiver_user_id, message_id);
			return null;
		}
		else
		{
			const sender_name = admin.database().ref(`/Users/${sender_user_id}/name`).once('value');
			return sender_name.then(result =>
			{
				const sender_name2 = result.val();
				
				const deviceToken = admin.database().ref(`/Users/${receiver_user_id}/device_token`).once('value');
				return deviceToken.then(result =>
				{
					const token_id = result.val();
					const payload =
					{
						notification:
					   {
					   title: `${sender_name2}`,
						body: `you got new message`,
						icon: "default",
						sound: "default"
					   }
					  };

					return admin.messaging().sendToDevice(token_id, payload).then(response =>
					{
							console.log('Dziala to' , receiver_user_id, message_id, token_id);
					});
				});
				
			});
			
			
			
		}
		
		
	});
	
	

})